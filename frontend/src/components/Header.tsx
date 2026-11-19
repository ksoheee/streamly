"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";

export default function Header() {
  const { me, loading, logout } = useAuth();
  const router = useRouter();

  return (
    <header className="header">
      <Link href="/" className="logo">
        Streamly
      </Link>
      <nav className="nav">
        {loading ? null : me ? (
          <>
            <Link href="/broadcasts/new" className="btn primary">
              방송하기
            </Link>
            <span className="muted">{me.username}</span>
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
            <Link href="/signup" className="btn">
              회원가입
            </Link>
          </>
        )}
      </nav>
    </header>
  );
}
