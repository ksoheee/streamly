"use client";

import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { api, type Broadcast } from "@/lib/api";

const NAV = [
  { href: "/", label: "전체 방송", status: null },
  { href: "/?status=LIVE", label: "방송 중", status: "LIVE" },
  { href: "/?status=ENDED", label: "종료된 방송", status: "ENDED" },
];

export default function Sidebar() {
  const pathname = usePathname();
  const status = useSearchParams().get("status");
  const [live, setLive] = useState<Broadcast[]>([]);

  useEffect(() => {
    const load = () =>
      api<Broadcast[]>("GET", "/api/broadcasts?status=LIVE")
        .then(setLive)
        .catch(() => {});
    load();
    const t = setInterval(load, 10000);
    return () => clearInterval(t);
  }, []);

  return (
    <aside className="sidebar">
      <nav>
        {NAV.map((n) => (
          <Link
            key={n.href}
            href={n.href}
            className={`side-link ${pathname === "/" && status === n.status ? "active" : ""}`}
          >
            {n.label}
          </Link>
        ))}
        <Link href="/recordings" className={`side-link ${pathname.startsWith("/recordings") ? "active" : ""}`}>
          다시보기
        </Link>
      </nav>
      <p className="side-heading">지금 방송 중</p>
      {live.length === 0 ? (
        <p className="muted side-empty">방송 중인 채널이 없어요</p>
      ) : (
        live.map((b) => (
          <Link key={b.id} href={`/broadcasts/${b.id}`} className="side-channel">
            <span className="avatar sm">{b.ownerName?.slice(0, 1).toUpperCase()}</span>
            <span className="side-channel-text">
              <strong>{b.ownerName}</strong>
              <span className="muted">{b.title}</span>
            </span>
            <span className="side-viewers">● {b.viewerCount ?? 0}</span>
          </Link>
        ))
      )}
    </aside>
  );
}
