"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { api, type Broadcast } from "@/lib/api";
import { useNow } from "@/lib/time";
import BroadcastCard from "@/components/BroadcastCard";

const TABS = [
  { status: "", label: "전체" },
  { status: "LIVE", label: "방송 중" },
  { status: "ENDED", label: "종료" },
];

export default function HomePage() {
  const params = useSearchParams();
  const status = params.get("status") ?? "";
  const q = params.get("q") ?? "";
  const [items, setItems] = useState<Broadcast[] | null>(null);
  const [error, setError] = useState("");
  const now = useNow();

  useEffect(() => {
    const qs = new URLSearchParams();
    if (status) qs.set("status", status);
    if (q) qs.set("q", q);
    const load = () =>
      api<Broadcast[]>("GET", `/api/broadcasts${qs.size ? `?${qs}` : ""}`)
        .then((list) => {
          setItems(list);
          setError("");
        })
        .catch((e) => setError(e.message));
    setItems(null);
    load();
    const t = setInterval(load, 5000);
    return () => clearInterval(t);
  }, [status, q]);

  const tabHref = (s: string) => {
    const qs = new URLSearchParams();
    if (s) qs.set("status", s);
    if (q) qs.set("q", q);
    return qs.size ? `/?${qs}` : "/";
  };

  return (
    <>
      <div className="row between">
        <h1>{q ? `'${q}' 검색 결과` : "방송 목록"}</h1>
        <div className="tabs" role="tablist" aria-label="상태 필터">
          {TABS.map((t) => (
            <Link
              key={t.status}
              href={tabHref(t.status)}
              role="tab"
              aria-selected={status === t.status}
              className={`tab ${status === t.status ? "active" : ""}`}
            >
              {t.label}
            </Link>
          ))}
        </div>
      </div>
      {error && <p className="error">{error}</p>}
      {items === null ? (
        <p className="muted">불러오는 중…</p>
      ) : items.length === 0 ? (
        <p className="muted">{q ? "검색 결과가 없어요." : "방송이 없어요."}</p>
      ) : (
        <ul className="grid">
          {items.map((b) => (
            <li key={b.id}>
              <BroadcastCard b={b} now={now} />
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
