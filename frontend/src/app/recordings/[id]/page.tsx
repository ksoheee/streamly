"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { api, type Recording } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { duration } from "@/lib/time";

export default function RecordingPage() {
  const { id } = useParams<{ id: string }>();
  const { me } = useAuth();
  const router = useRouter();
  const [r, setR] = useState<Recording | null>(null);
  const [error, setError] = useState("");
  const [confirming, setConfirming] = useState(false);

  useEffect(() => {
    api<Recording>("GET", `/api/recordings/${id}`)
      .then(setR)
      .catch((e) => setError(e.message));
  }, [id]);

  if (error && !r) return <p className="error">{error}</p>;
  if (!r) return <p className="muted">불러오는 중…</p>;

  return (
    <div className="narrow-wide">
      <div className="player">
        <video src={`/api/recordings/${r.id}/video`} controls playsInline preload="metadata" />
      </div>
      <h1 className="watch-title">{r.title}</h1>
      <p className="muted watch-meta">
        <span className="badge ended">다시보기</span>
        <span>{duration(r.durationSeconds)}</span>
        <span>· {r.createdAt && new Date(r.createdAt).toLocaleString("ko-KR")} 녹화</span>
      </p>
      <div className="row between owner-row">
        <div className="row">
          <span className="avatar">{r.ownerName?.slice(0, 1).toUpperCase()}</span>
          <strong>{r.ownerName}</strong>
        </div>
        <div className="row">
          <Link href={`/broadcasts/${r.broadcastId}`} className="btn">
            방송 정보·댓글
          </Link>
          {me?.id === r.ownerId &&
            (confirming ? (
              <>
                <button
                  className="btn danger"
                  onClick={async () => {
                    try {
                      await api("DELETE", `/api/recordings/${r.id}`);
                      router.push("/recordings");
                    } catch (e) {
                      setError((e as Error).message);
                    }
                  }}
                >
                  정말 삭제
                </button>
                <button className="btn" onClick={() => setConfirming(false)}>
                  취소
                </button>
              </>
            ) : (
              <button className="btn danger" onClick={() => setConfirming(true)}>
                녹화 삭제
              </button>
            ))}
          <Link href="/recordings" className="btn">
            목록
          </Link>
        </div>
      </div>
      {error && <p className="error">{error}</p>}
    </div>
  );
}
