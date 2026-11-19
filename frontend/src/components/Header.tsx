"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth";
import Logo from "@/components/Logo";

export default function Header() {
  const { me, loading, logout } = useAuth();
  const router = useRouter();
  const params = useSearchParams();
  const [q, setQ] = useState(params.get("q") ?? "");

  useEffect(() => setQ(params.get("q") ?? ""), [params]);

  return (
    <header className="header">
      <Link href="/" className="logo" aria-label="Streamly 홈">
        <Logo />
      </Link>
      <form
        className="search"
        role="search"
        onSubmit={(e) => {
          e.preventDefault();
          router.push(q.trim() ? `/?q=${encodeURIComponent(q.trim())}` : "/");
        }}
      >
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="방송 제목, 방송자 검색"
          aria-label="방송 검색"
        />
      </form>
      <nav className="nav">
        {loading ? null : me ? (
          <>
            <Link href="/broadcasts/new" className="btn primary">
              방송하기
            </Link>
            <span className="muted hide-sm">{me.username}</span>
            <button
              className="btn"
              onClick={async () => {
                await logout();
                router.push("/");
              }}
            >
              로그아웃
            </button>
          </>
        ) : (
          <>
            <Link href="/login" className="btn primary">
              로그인
            </Link>
            <Link href="/signup" className="btn hide-sm">
              회원가입
            </Link>
          </>
        )}
      </nav>
    </header>
  );
}
