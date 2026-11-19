"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api, type Broadcast } from "@/lib/api";
import StatusBadge from "@/components/StatusBadge";

type Filter = "" | "LIVE" | "ENDED";

export default function HomePage() {
  const [items, setItems] = useState<Broadcast[] | null>(null);
  const [filter, setFilter] = useState<Filter>("");
  const [error, setError] = useState("");

  useEffect(() => {
    const load = () =>
      api<Broadcast[]>("GET", `/api/broadcasts${filter ? `?status=${filter}` : ""}`)
        .then((list) => {
          setItems(list);
          setError("");
        })
        .catch((e) => setError(e.message));
    load();
    const t = setInterval(load, 5000);
    return () => clearInterval(t);
  }, [filter]);

  return (
    <>
      <div className="row between">
        <h1>방송 목록</h1>
        <select value={filter} onChange={(e) => setFilter(e.target.value as Filter)} aria-label="상태 필터">
          <option value="">전체</option>
          <option value="LIVE">방송 중</option>
          <option value="ENDED">종료</option>
        </select>
      </div>
      {error && <p className="error">{error}</p>}
      {items === null ? (
        <p className="muted">불러오는 중…</p>
      ) : items.length === 0 ? (
        <p className="muted">방송이 없습니다.</p>
      ) : (
        <ul className="list">
          {items.map((b) => (
            <li key={b.id}>
              <Link href={`/broadcasts/${b.id}`} className="card">
                <StatusBadge b={b} />
                <strong className="title">{b.title}</strong>
                <span className="muted">{b.ownerName}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
