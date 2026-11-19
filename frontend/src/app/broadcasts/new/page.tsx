"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { api, type Broadcast } from "@/lib/api";
import { useAuth } from "@/lib/auth";

export default function NewBroadcastPage() {
  const { me, loading } = useAuth();
  const router = useRouter();
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    if (!loading && !me) router.replace("/login");
  }, [loading, me, router]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const b = await api<Broadcast>("POST", "/api/broadcasts", { title, description });
      router.push(`/broadcasts/${b.id}/studio`);
    } catch (err) {
      setError((err as Error).message);
    }
  };

  return (
    <div className="narrow">
      <h1>방송 만들기</h1>
      <form onSubmit={submit} className="form">
        <label>
          제목
          <input value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={100} />
        </label>
        <label>
          설명
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={1000}
            rows={3}
          />
        </label>
        {error && <p className="error">{error}</p>}
        <button className="btn primary">만들고 송출 화면으로</button>
      </form>
    </div>
  );
}
