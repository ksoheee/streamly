// 송출(WHIP)·시청(WHEP) 공통 로직. stream-test 키트(studio.html, watch.html)에서 실기기로 검증한 방식을 옮겼다.

export function waitIceGathering(pc: RTCPeerConnection, timeoutMs = 3000): Promise<void> {
  if (pc.iceGatheringState === "complete") return Promise.resolve();
  return new Promise((resolve) => {
    const t = setTimeout(resolve, timeoutMs);
    pc.addEventListener("icegatheringstatechange", () => {
      if (pc.iceGatheringState === "complete") {
        clearTimeout(t);
        resolve();
      }
    });
  });
}

// H.264(packetization-mode=1) 우선. mode=0 은 큰 프레임(화면 공유)을 쪼개지 못해 시청 쪽 디코딩이 깨질 수 있다.
export function preferH264(transceiver: RTCRtpTransceiver) {
  try {
    const codecs = RTCRtpSender.getCapabilities("video")?.codecs ?? [];
    const h264 = codecs.filter(
      (c) => c.mimeType === "video/H264" && /packetization-mode=1/.test(c.sdpFmtpLine ?? ""),
    );
    if (h264.length) {
      transceiver.setCodecPreferences([...h264, ...codecs.filter((c) => !h264.includes(c))]);
    }
  } catch {
    // 지원하지 않는 브라우저는 기본 코덱 사용
  }
}

export type Session = { pc: RTCPeerConnection; sessionUrl: string | null };

async function negotiate(
  pc: RTCPeerConnection,
  url: string,
  headers: Record<string, string> = {},
): Promise<string | null> {
  await pc.setLocalDescription(await pc.createOffer());
  await waitIceGathering(pc);
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/sdp", ...headers },
    body: pc.localDescription!.sdp,
  });
  if (res.status !== 201) throw new Error(`HTTP ${res.status}`);
  const location = res.headers.get("Location");
  await pc.setRemoteDescription({ type: "answer", sdp: await res.text() });
  return location;
}

export async function whip(
  stream: MediaStream,
  streamPath: string,
  token: string,
): Promise<Session & { videoSender: RTCRtpSender | null }> {
  const pc = new RTCPeerConnection();
  let videoSender: RTCRtpSender | null = null;
  for (const track of stream.getTracks()) {
    const tr = pc.addTransceiver(track, { direction: "sendonly" });
    if (track.kind === "video") {
      preferH264(tr);
      videoSender = tr.sender;
    }
  }
  try {
    const sessionUrl = await negotiate(pc, `/${streamPath}/whip`, {
      Authorization: `Bearer ${token}`,
    });
    return { pc, sessionUrl, videoSender };
  } catch (e) {
    pc.close();
    throw new Error(`송출 연결 실패: ${(e as Error).message}`);
  }
}

export async function whep(streamPath: string, onTrack: (s: MediaStream) => void): Promise<Session> {
  const pc = new RTCPeerConnection();
  pc.addTransceiver("video", { direction: "recvonly" });
  pc.addTransceiver("audio", { direction: "recvonly" });
  pc.ontrack = (e) => onTrack(e.streams[0]);
  try {
    const sessionUrl = await negotiate(pc, `/${streamPath}/whep`);
    return { pc, sessionUrl };
  } catch (e) {
    pc.close();
    throw e;
  }
}

export async function closeSession(s: Session | null) {
  if (!s) return;
  if (s.sessionUrl) {
    try {
      await fetch(s.sessionUrl, { method: "DELETE" });
    } catch {}
  }
  s.pc.close();
}

// Chrome은 공유 화면이 멈춰 있으면 새 프레임을 보내지 않는다. 그러면 나중에 들어온 시청자는 키프레임을 못 받아
// 소리만 나오고 영상이 검게 남는다. 화면이 멈춘 동안 마지막 프레임을 0.5초마다 다시 보낸다.
// MediaStreamTrackProcessor/Generator 가 없는 브라우저(사파리 등)는 원래 트랙을 그대로 쓴다.
type FrameReader = { read(): Promise<{ value?: VideoFrame; done: boolean }> };
type FrameWriter = { write(f: VideoFrame): Promise<void> };
declare const MediaStreamTrackProcessor: new (o: { track: MediaStreamTrack }) => {
  readable: { getReader(): FrameReader };
};
declare const MediaStreamTrackGenerator: new (o: { kind: "video" }) => MediaStreamTrack & {
  writable: { getWriter(): FrameWriter };
};

export function keepFramesFlowing(track: MediaStreamTrack, idleMs = 500): MediaStreamTrack {
  if (!("MediaStreamTrackProcessor" in window) || !("MediaStreamTrackGenerator" in window)) return track;
  const reader = new MediaStreamTrackProcessor({ track }).readable.getReader();
  const gen = new MediaStreamTrackGenerator({ kind: "video" });
  const writer = gen.writable.getWriter();
  let last: VideoFrame | null = null;
  let lastAt = 0;
  const push = (f: VideoFrame) => {
    lastAt = performance.now();
    writer.write(new VideoFrame(f, { timestamp: Math.round(lastAt * 1000) })).catch(() => {});
  };
  // 송출 탭이 뒤로 가면 탭의 setInterval 은 1초~1분에 한 번으로 느려진다. 워커 타이머는 느려지지 않아 워커가 박자를 준다.
  const ticker = new Worker(
    URL.createObjectURL(
      new Blob([`setInterval(() => postMessage(0), ${Math.round(idleMs / 2)})`], {
        type: "text/javascript",
      }),
    ),
  );
  ticker.onmessage = () => {
    if (last && performance.now() - lastAt >= idleMs) push(last);
  };
  void (async () => {
    for (;;) {
      const { value, done } = await reader.read();
      if (done || !value) break;
      last?.close();
      last = value;
      push(value);
    }
    ticker.terminate();
    last?.close();
    gen.stop();
  })();
  return gen;
}
