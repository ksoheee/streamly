"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { api, type Broadcast, type PublishToken } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { closeSession, whip, type Session, keepFramesFlowing } from "@/lib/webrtc";

type Kind = "pc" | "mobile";
type Facing = "user" | "environment";

export default function StudioPage() {
  const { id } = useParams<{ id: string }>();
  const { me, loading } = useAuth();
  const router = useRouter();
  const [b, setB] = useState<Broadcast | null>(null);
  const [kind, setKind] = useState<Kind>("pc");
  const [canShareScreen, setCanShareScreen] = useState(true);
  const [facing, setFacing] = useState<Facing>("user");
  const [live, setLive] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  const previewRef = useRef<HTMLVideoElement>(null);
  const sessionRef = useRef<Session | null>(null);
  const videoSenderRef = useRef<RTCRtpSender | null>(null);
  const tracksRef = useRef<MediaStreamTrack[]>([]);
  const audioCtxRef = useRef<AudioContext | null>(null);
  const endingRef = useRef(false);

  useEffect(() => {
    const screen = !!navigator.mediaDevices?.getDisplayMedia;
    const touch = window.matchMedia("(pointer: coarse)").matches;
    setCanShareScreen(screen);
    setKind(screen && !touch ? "pc" : "mobile");
    if (!window.isSecureContext) setError("HTTPS가 아니라서 화면 공유와 카메라를 쓸 수 없습니다.");
  }, []);

  useEffect(() => {
    if (!loading && !me) router.replace("/login");
  }, [loading, me, router]);

  useEffect(() => {
    api<Broadcast>("GET", `/api/broadcasts/${id}`)
      .then(setB)
      .catch((e) => setError(e.message));
  }, [id]);

  const releaseMedia = useCallback(async () => {
    const s = sessionRef.current;
    sessionRef.current = null;
    videoSenderRef.current = null;
    await closeSession(s);
    tracksRef.current.forEach((t) => t.stop());
    tracksRef.current = [];
    await audioCtxRef.current?.close().catch(() => {});
    audioCtxRef.current = null;
    if (previewRef.current) previewRef.current.srcObject = null;
    setLive(false);
  }, []);

  // 방송 종료: 송출 연결을 끊고 서버에 종료를 알린다. 종료된 방송은 다시 송출할 수 없다.
  const end = useCallback(async () => {
    if (endingRef.current) return;
    endingRef.current = true;
    setBusy(true);
    await releaseMedia();
    try {
      setB(await api<Broadcast>("POST", `/api/broadcasts/${id}/end`));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }, [id, releaseMedia]);

  useEffect(() => {
    return () => {
      void releaseMedia();
    };
  }, [releaseMedia]);

  // 화면(탭/시스템 소리 포함) + 마이크. 두 소리를 Web Audio로 하나의 트랙으로 섞는다.
  async function captureForPc(): Promise<MediaStream> {
    const display = await navigator.mediaDevices.getDisplayMedia({
      // 레티나 화면 원본 해상도는 휴대폰 H.264 디코더 한도를 넘어 시청 시 영상만 안 나온다. 720p로 낮춘다.
      video: { frameRate: 30, width: { max: 1280 }, height: { max: 720 } },
      audio: { suppressLocalAudioPlayback: false },
      systemAudio: "include",
    } as DisplayMediaStreamOptions);
    tracksRef.current.push(...display.getTracks());
    const mic = await navigator.mediaDevices.getUserMedia({
      audio: { echoCancellation: true, noiseSuppression: true },
    });
    tracksRef.current.push(...mic.getTracks());

    const ctx = new AudioContext();
    audioCtxRef.current = ctx;
    await ctx.resume();
    const dest = ctx.createMediaStreamDestination();
    const tabAudio = display.getAudioTracks();
    if (tabAudio.length) {
      ctx.createMediaStreamSource(new MediaStream(tabAudio)).connect(dest);
      setNotice("");
    } else {
      setNotice(
        "탭/시스템 소리가 잡히지 않았습니다. 공유할 때 'Chrome 탭'을 고르고 '탭 오디오도 공유'를 켜 주세요. (윈도우는 '전체 화면'에서 시스템 소리 공유 가능)",
      );
    }
    ctx.createMediaStreamSource(mic).connect(dest);

    const video = display.getVideoTracks()[0];
    // 브라우저의 '공유 중지'를 누르면 방송도 끝낸다
    video.onended = () => void end();
    return new MediaStream([keepFramesFlowing(video), dest.stream.getAudioTracks()[0]]);
  }

  async function captureForMobile(): Promise<MediaStream> {
    const cam = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: facing }, width: { ideal: 1280 }, height: { ideal: 720 } },
      audio: { echoCancellation: true, noiseSuppression: true },
    });
    tracksRef.current.push(...cam.getTracks());
    return cam;
  }

  async function start() {
    setBusy(true);
    setError("");
    endingRef.current = false;
    try {
      const token = await api<PublishToken>("POST", `/api/broadcasts/${id}/publish-token`);
      if (!token.token || !token.streamPath) throw new Error("송출 토큰을 받지 못했습니다.");
      const stream = kind === "pc" ? await captureForPc() : await captureForMobile();
      if (previewRef.current) previewRef.current.srcObject = stream;
      const s = await whip(stream, token.streamPath, token.token);
      sessionRef.current = s;
      videoSenderRef.current = s.videoSender;
      s.pc.onconnectionstatechange = () => {
        if (s.pc.connectionState === "failed") setNotice("송출 연결이 끊겼습니다. 30초 안에 다시 시작하면 방송이 이어집니다.");
      };
      setLive(true);
    } catch (e) {
      const err = e as Error;
      setError(err.name === "NotAllowedError" ? "화면 공유/카메라/마이크 권한이 거부되었습니다." : err.message);
      await releaseMedia();
    } finally {
      setBusy(false);
    }
  }

  // 방송을 끊지 않고 카메라만 바꾼다. 휴대폰은 카메라 두 개를 동시에 못 여는 경우가 많아 기존 카메라를 먼저 끈다.
  async function flipCamera() {
    const sender = videoSenderRef.current;
    if (!sender) return;
    const next: Facing = facing === "user" ? "environment" : "user";
    setBusy(true);
    const old = sender.track;
    if (old) {
      old.stop();
      tracksRef.current = tracksRef.current.filter((t) => t !== old);
    }
    const open = async (f: Facing) =>
      (
        await navigator.mediaDevices.getUserMedia({
          video: { facingMode: { exact: f }, width: { ideal: 1280 }, height: { ideal: 720 } },
        })
      ).getVideoTracks()[0];
    let track: MediaStreamTrack | null = null;
    let used: Facing = next;
    try {
      track = await open(next);
    } catch {
      used = facing;
      setNotice("카메라 전환에 실패해 원래 카메라로 돌아갑니다.");
      track = await open(facing).catch(() => null);
    }
    if (track) {
      await sender.replaceTrack(track);
      tracksRef.current.push(track);
      const preview = previewRef.current;
      const audio = (preview?.srcObject as MediaStream | null)?.getAudioTracks() ?? [];
      if (preview) preview.srcObject = new MediaStream([track, ...audio]);
      setFacing(used);
    }
    setBusy(false);
  }

  if (!b) return error ? <p className="error">{error}</p> : <p className="muted">불러오는 중…</p>;
  if (me && b.ownerId !== me.id) return <p className="error">방송 소유자만 송출할 수 있습니다.</p>;

  if (b.status === "ENDED") {
    return (
      <div className="narrow">
        <h1>{b.title}</h1>
        <p className="notice">방송이 종료되었습니다. 다시 방송하려면 새 방송을 만들어 주세요.</p>
        <div className="row">
          <Link href="/broadcasts/new" className="btn primary">
            새 방송 만들기
          </Link>
          <Link href={`/broadcasts/${b.id}`} className="btn">
            방송 페이지
          </Link>
        </div>
      </div>
    );
  }

  return (
    <>
      <div className="row between">
        <h1>{b.title}</h1>
        <Link href={`/broadcasts/${b.id}`} className="btn" target="_blank">
          시청 화면 열기
        </Link>
      </div>

      <div className={`player ${kind === "mobile" ? "mobile" : ""}`}>
        <video ref={previewRef} autoPlay playsInline muted />
        {!live && <div className="overlay">방송 시작을 누르면 미리보기가 나옵니다</div>}
      </div>

      {!live && (
        <div className="row" style={{ marginBottom: 12 }}>
          <label className="row">
            <input type="radio" checked={kind === "pc"} disabled={!canShareScreen} onChange={() => setKind("pc")} />
            PC 화면 공유
          </label>
          <label className="row">
            <input type="radio" checked={kind === "mobile"} onChange={() => setKind("mobile")} />
            카메라
          </label>
          {kind === "mobile" && (
            <select value={facing} onChange={(e) => setFacing(e.target.value as Facing)} aria-label="카메라 선택">
              <option value="user">전면 카메라</option>
              <option value="environment">후면 카메라</option>
            </select>
          )}
        </div>
      )}

      <div className="row">
        {!live ? (
          <button className="btn primary" disabled={busy} onClick={start}>
            방송 시작
          </button>
        ) : (
          <>
            <button className="btn danger" disabled={busy} onClick={end}>
              방송 종료
            </button>
            {kind === "mobile" && (
              <button className="btn" disabled={busy} onClick={flipCamera}>
                카메라 전환
              </button>
            )}
            <span className="badge live">● 송출 중</span>
          </>
        )}
      </div>

      {kind === "pc" && !live && (
        <p className="muted">Chrome에서 공유할 &apos;탭&apos;을 고르고 &apos;탭 오디오도 공유&apos;를 켜면 탭 소리가 마이크와 함께 송출됩니다.</p>
      )}
      {notice && <p className="notice">{notice}</p>}
      {error && <p className="error">{error}</p>}
    </>
  );
}
