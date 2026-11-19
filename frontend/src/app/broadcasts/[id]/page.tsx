"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { api, ApiError, type Broadcast } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { closeSession, whep, type Session } from "@/lib/webrtc";
import StatusBadge from "@/components/StatusBadge";
import Chat from "@/components/Chat";
import Comments from "@/components/Comments";
import BroadcastRecordings from "@/components/BroadcastRecordings";
import ConfirmDialog from "@/components/ConfirmDialog";
import { elapsed, useNow } from "@/lib/time";

export default function BroadcastPage() {
  const { id } = useParams<{ id: string }>();
  const { me } = useAuth();
  const router = useRouter();
  const [b, setB] = useState<Broadcast | null>(null);
  const [error, setError] = useState("");
  const [connected, setConnected] = useState(false);
  const [muted, setMuted] = useState(true);
  const [editing, setEditing] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const videoRef = useRef<HTMLVideoElement>(null);
  const sessionRef = useRef<Session | null>(null);
  const connectingRef = useRef(false);
  const now = useNow();

  const disconnect = useCallback(async () => {
    const s = sessionRef.current;
    sessionRef.current = null;
    setConnected(false);
    await closeSession(s);
  }, []);

  // 연결이 끊겨도 여기서는 종료로 판단하지 않는다. 종료 여부는 서버 상태(status)가 정한다.
  const connect = useCallback(
    async (streamPath: string) => {
      if (sessionRef.current || connectingRef.current) return;
      connectingRef.current = true;
      try {
        const s = await whep(streamPath, (stream) => {
          if (videoRef.current && videoRef.current.srcObject !== stream) {
            videoRef.current.srcObject = stream;
          }
        });
        let dropTimer: ReturnType<typeof setTimeout> | undefined;
        s.pc.onconnectionstatechange = () => {
          const st = s.pc.connectionState;
          clearTimeout(dropTimer);
          if (st === "connected") setConnected(true);
          else if (st === "disconnected") dropTimer = setTimeout(() => sessionRef.current === s && disconnect(), 2000);
          else if (st === "failed" || st === "closed") {
            if (sessionRef.current === s) disconnect();
          }
        };
        sessionRef.current = s;
      } catch {
        // 아직 송출이 MediaMTX에 도착하지 않았을 수 있다. 다음 상태 확인 때 다시 시도한다.
      } finally {
        connectingRef.current = false;
      }
    },
    [disconnect],
  );

  const load = useCallback(async () => {
    try {
      setB(await api<Broadcast>("GET", `/api/broadcasts/${id}`));
      setError("");
    } catch (e) {
      setError(e instanceof ApiError && e.status === 404 ? "방송이 없습니다." : (e as Error).message);
    }
  }, [id]);

  useEffect(() => {
    load();
    const t = setInterval(load, 3000);
    return () => clearInterval(t);
  }, [load]);

  useEffect(() => {
    if (!b) return;
    if (b.status === "LIVE" && !b.reconnecting && b.streamPath) connect(b.streamPath);
    if (b.status === "ENDED") disconnect();
  }, [b, connect, disconnect]);

  useEffect(() => {
    const onHide = () => {
      void disconnect();
    };
    window.addEventListener("pagehide", onHide);
    return () => {
      window.removeEventListener("pagehide", onHide);
      void disconnect();
    };
  }, [disconnect]);

  if (error && !b) return <p className="error">{error}</p>;
  if (!b) return <p className="muted">불러오는 중…</p>;

  const isOwner = me?.id === b.ownerId;
  let overlay: string | null = null;
  if (b.status === "ENDED") overlay = "방송이 종료되었습니다";
  else if (b.status === "READY") overlay = "아직 방송 전입니다";
  else if (b.reconnecting) overlay = "잠시 연결이 끊겼습니다. 다시 연결 중…";
  else if (!connected) overlay = "연결 중…";

  return (
    <div className="watch">
      <div className="watch-main">
        <div className="player">
          <video ref={videoRef} autoPlay playsInline muted={muted} />
          {overlay && <div className="overlay">{overlay}</div>}
          {b.status === "LIVE" && connected && muted && (
            <button
              className="btn primary unmute"
              onClick={() => {
                setMuted(false);
                videoRef.current?.play();
              }}
            >
              소리 켜기
            </button>
          )}
        </div>

        {editing ? (
          <EditForm
            b={b}
            onDone={(updated) => {
              if (updated) setB(updated);
              setEditing(false);
            }}
          />
        ) : (
          <h1 className="watch-title">{b.title}</h1>
        )}
        <p className="muted watch-meta">
          <StatusBadge b={b} />
          {b.status === "LIVE" && (
            <>
              <span>{b.viewerCount ?? 0}명 시청 중</span>
              <span>· {elapsed(b.startedAt, now)} 스트리밍 중</span>
            </>
          )}
        </p>

        <div className="row between owner-row">
          <div className="row">
            <span className="avatar">{b.ownerName?.slice(0, 1).toUpperCase()}</span>
            <strong>{b.ownerName}</strong>
          </div>
          <div className="row">
            {isOwner && !editing && (
              <>
                {b.status !== "ENDED" && (
                  <Link href={`/broadcasts/${b.id}/studio`} className="btn primary">
                    송출 화면으로
                  </Link>
                )}
                <button className="btn" onClick={() => setEditing(true)}>
                  수정
                </button>
                <button
                  className="btn danger"
                  onClick={() => setConfirmDelete(true)}
                >
                  삭제
                </button>
              </>
            )}
            <Link href="/" className="btn">
              나가기
            </Link>
          </div>
        </div>
        {b.status === "ENDED" && <BroadcastRecordings broadcastId={Number(id)} />}
        {!editing && b.description && <p className="watch-desc">{b.description}</p>}
        {error && <p className="error">{error}</p>}
        <Comments broadcastId={Number(id)} />
        <ConfirmDialog
          open={confirmDelete}
          message="방송을 삭제할까요? 방송 중이면 송출도 끊겨요."
          onCancel={() => setConfirmDelete(false)}
          onConfirm={async () => {
            setConfirmDelete(false);
            try {
              await api("DELETE", `/api/broadcasts/${b.id}`);
              router.push("/");
            } catch (e) {
              setError((e as Error).message);
            }
          }}
        />
      </div>
      <Chat broadcastId={Number(id)} ended={b.status === "ENDED"} />
    </div>
  );
}

function EditForm({ b, onDone }: { b: Broadcast; onDone: (b?: Broadcast) => void }) {
  const [title, setTitle] = useState(b.title);
  const [description, setDescription] = useState(b.description ?? "");
  const [error, setError] = useState("");

  return (
    <form
      className="form"
      onSubmit={async (e) => {
        e.preventDefault();
        try {
          onDone(await api<Broadcast>("PUT", `/api/broadcasts/${b.id}`, { title, description }));
        } catch (err) {
          setError((err as Error).message);
        }
      }}
    >
      <label>
        제목
        <input value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={100} />
      </label>
      <label>
        설명
        <textarea value={description} onChange={(e) => setDescription(e.target.value)} maxLength={1000} rows={3} />
      </label>
      {error && <p className="error">{error}</p>}
      <div className="row">
        <button className="btn primary">저장</button>
        <button type="button" className="btn" onClick={() => onDone()}>
          취소
        </button>
      </div>
    </form>
  );
}
