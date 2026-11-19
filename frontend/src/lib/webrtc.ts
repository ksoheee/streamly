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
