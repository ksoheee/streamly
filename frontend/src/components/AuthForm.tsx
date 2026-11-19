"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth";

export default function AuthForm({ mode }: { mode: "login" | "signup" }) {
  const { refresh } = useAuth();
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function run(action: () => Promise<unknown>) {
    setBusy(true);
    setError("");
    try {
      await action();
      await refresh();
      router.push("/");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    run(async () => {
      if (mode === "signup") await api("POST", "/api/auth/signup", { username, password });
      await api("POST", "/api/auth/login", { username, password });
    });
  };

  const demo = (account: "owner" | "viewer") =>
    run(() => api("POST", "/api/auth/demo-login", { account }));

  return (
    <div className="narrow">
      <h1>{mode === "login" ? "로그인" : "회원가입"}</h1>
      <form onSubmit={submit} className="form">
        <label>
          아이디 {mode === "signup" && <span className="muted">(영문·숫자·_·- 3~30자, 이메일 불가)</span>}
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
            minLength={3}
            maxLength={30}
            pattern={mode === "signup" ? "[A-Za-z0-9_\\-]+" : undefined}
            title={mode === "signup" ? "영문, 숫자, _, - 만 쓸 수 있어요" : undefined}
            autoComplete="username"
          />
        </label>
        <label>
          비밀번호 {mode === "signup" && <span className="muted">(8자 이상)</span>}
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={mode === "signup" ? 8 : 1}
            autoComplete={mode === "login" ? "current-password" : "new-password"}
          />
        </label>
        {error && <p className="error">{error}</p>}
        <button className="btn primary" disabled={busy}>
          {mode === "login" ? "로그인" : "가입하고 로그인"}
        </button>
      </form>

      <div className="demo">
        <p className="muted">심사용 테스트 계정으로 바로 로그인</p>
        <div className="row">
          <button className="btn" disabled={busy} onClick={() => demo("owner")}>
            테스트 계정 1 (방송자)
          </button>
          <button className="btn" disabled={busy} onClick={() => demo("viewer")}>
            테스트 계정 2 (다른 사용자)
          </button>
        </div>
      </div>

      <p className="muted">
        {mode === "login" ? (
          <>
            계정이 없나요? <Link href="/signup">회원가입</Link>
          </>
        ) : (
          <>
            이미 계정이 있나요? <Link href="/login">로그인</Link>
          </>
        )}
      </p>
    </div>
  );
}
