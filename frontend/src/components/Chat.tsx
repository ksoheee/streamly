"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { api, type ChatMessage } from "@/lib/api";
import { useAuth } from "@/lib/auth";

// 2초마다 마지막으로 받은 메시지 이후만 가져온다
export default function Chat({ broadcastId, ended }: { broadcastId: number; ended: boolean }) {
  const { me } = useAuth();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [text, setText] = useState("");
  const [error, setError] = useState("");
  const lastId = useRef(0);
  const listRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    lastId.current = 0;
    setMessages([]);
    let stop = false;
    const load = async () => {
      try {
        const next = await api<ChatMessage[]>(
          "GET",
          `/api/broadcasts/${broadcastId}/chats?after=${lastId.current}`,
        );
        if (stop || next.length === 0) return;
        lastId.current = next[next.length - 1].id ?? lastId.current;
        setMessages((prev) => [...prev, ...next].slice(-200));
      } catch {}
    };
    load();
    const t = setInterval(load, 2000);
    return () => {
      stop = true;
      clearInterval(t);
    };
  }, [broadcastId]);

  useEffect(() => {
    const el = listRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [messages]);

  async function send(e: React.FormEvent) {
    e.preventDefault();
    const content = text.trim();
    if (!content) return;
    setError("");
    try {
      const m = await api<ChatMessage>("POST", `/api/broadcasts/${broadcastId}/chats`, { content });
      setText("");
      if ((m.id ?? 0) > lastId.current) {
        lastId.current = m.id ?? lastId.current;
        setMessages((prev) => [...prev, m]);
      }
    } catch (err) {
      setError((err as Error).message);
    }
  }

  return (
    <section className="chat" aria-label="채팅">
      <div className="chat-head">채팅</div>
      <div className="chat-list" ref={listRef}>
        {messages.length === 0 && <p className="muted chat-empty">첫 채팅을 남겨 보세요</p>}
        {messages.map((m) => (
          <p key={m.id} className="chat-msg">
            <span className="chat-name">{m.username}</span> {m.content}
          </p>
        ))}
      </div>
      {ended ? (
        <p className="muted chat-foot">종료된 방송이에요</p>
      ) : me ? (
        <form className="chat-form" onSubmit={send}>
          <input
            value={text}
            onChange={(e) => setText(e.target.value)}
            maxLength={200}
            placeholder="채팅을 입력하세요"
            aria-label="채팅 입력"
          />
          <button className="btn primary">전송</button>
        </form>
      ) : (
        <p className="muted chat-foot">
          <Link href="/login">로그인</Link>하면 채팅할 수 있어요
        </p>
      )}
      {error && <p className="error chat-foot">{error}</p>}
    </section>
  );
}
