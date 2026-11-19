"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api, type Recording } from "@/lib/api";
import { duration } from "@/lib/time";

export default function RecordingsPage() {
  const [items, setItems] = useState<Recording[] | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api<Recording[]>("GET", "/api/recordings")
      .then(setItems)
      .catch((e) => setError(e.message));
  }, []);

  return (
    <>
      <h1>다시보기</h1>
      <p className="muted">끝난 방송은 자동으로 녹화되어 7일 동안 다시 볼 수 있어요.</p>
      {error && <p className="error">{error}</p>}
      {items === null ? (
        <p className="muted">불러오는 중…</p>
      ) : items.length === 0 ? (
        <p className="muted">아직 녹화된 방송이 없어요.</p>
      ) : (
        <ul className="grid">
          {items.map((r) => (
            <li key={r.id}>
              <Link href={`/recordings/${r.id}`} className="bcard">
                <div className="thumb">
                  <span className="thumb-initial">{r.title?.slice(0, 1)}</span>
                  <span className="pill dim">다시보기</span>
                  <span className="pill dim time">{duration(r.durationSeconds)}</span>
                </div>
                <div className="bcard-info">
                  <span className="avatar">{r.ownerName?.slice(0, 1).toUpperCase()}</span>
                  <div>
                    <strong className="bcard-title">{r.title}</strong>
                    <span className="muted">
                      {r.ownerName} · {r.createdAt && new Date(r.createdAt).toLocaleString("ko-KR")}
                    </span>
                  </div>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
