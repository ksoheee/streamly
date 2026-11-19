"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { api, type Comment } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import ConfirmDialog from "@/components/ConfirmDialog";

const PAGE = 20;

export default function Comments({ broadcastId }: { broadcastId: number }) {
  const { me } = useAuth();
  const [items, setItems] = useState<Comment[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [text, setText] = useState("");
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editText, setEditText] = useState("");
  const [error, setError] = useState("");

  const load = useCallback(
    async (before?: number) => {
      const page = await api<Comment[]>(
        "GET",
        `/api/broadcasts/${broadcastId}/comments${before ? `?before=${before}` : ""}`,
      );
      setItems((prev) => (before ? [...prev, ...page] : page));
      setHasMore(page.length === PAGE);
    },
    [broadcastId],
  );

  useEffect(() => {
    load().catch((e) => setError(e.message));
  }, [load]);

  // 5초마다 최신 20개를 다시 받아 새 댓글·수정·삭제를 반영한다 (더 보기로 불러온 오래된 댓글은 유지)
  useEffect(() => {
    const t = setInterval(async () => {
      try {
        const latest = await api<Comment[]>("GET", `/api/broadcasts/${broadcastId}/comments`);
        const minId = latest.length ? (latest[latest.length - 1].id ?? 0) : Infinity;
        setItems((prev) => [...latest, ...prev.filter((x) => (x.id ?? 0) < minId)]);
      } catch {}
    }, 5000);
    return () => clearInterval(t);
  }, [broadcastId]);

  async function run(action: () => Promise<unknown>) {
    setError("");
    try {
      await action();
    } catch (e) {
      setError((e as Error).message);
    }
  }

  const create = (e: React.FormEvent) => {
    e.preventDefault();
    if (!text.trim()) return;
    run(async () => {
      const c = await api<Comment>("POST", `/api/broadcasts/${broadcastId}/comments`, { content: text });
      setItems((prev) => [c, ...prev]);
      setText("");
    });
  };

  const save = (id: number) =>
    run(async () => {
      const c = await api<Comment>("PUT", `/api/comments/${id}`, { content: editText });
      setItems((prev) => prev.map((x) => (x.id === id ? c : x)));
      setEditingId(null);
    });

  const [deletingId, setDeletingId] = useState<number | null>(null);
  const remove = (id: number) =>
    run(async () => {
      await api("DELETE", `/api/comments/${id}`);
      setItems((prev) => prev.filter((x) => x.id !== id));
    });

  return (
    <section className="comments" aria-label="댓글">
      <h2>댓글</h2>
      {me ? (
        <form className="comment-form" onSubmit={create}>
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            maxLength={500}
            rows={2}
            placeholder="방송에 댓글을 남겨 보세요"
            aria-label="댓글 입력"
          />
          <button className="btn primary">등록</button>
        </form>
      ) : (
        <p className="muted">
          <Link href="/login">로그인</Link>하면 댓글을 남길 수 있어요
        </p>
      )}
      {error && <p className="error">{error}</p>}
      {items.length === 0 && <p className="muted">아직 댓글이 없어요.</p>}
      <ul className="comment-list">
        {items.map((c) => (
          <li key={c.id} className="comment">
            <span className="avatar sm">{c.username?.slice(0, 1).toUpperCase()}</span>
            <div className="comment-body">
              <p className="comment-meta">
                <strong>{c.username}</strong>{" "}
                <span className="muted">
                  {c.createdAt && new Date(c.createdAt).toLocaleString("ko-KR")}
                  {c.edited && " (수정됨)"}
                </span>
              </p>
              {editingId === c.id ? (
                <div className="comment-form">
                  <textarea
                    value={editText}
                    onChange={(e) => setEditText(e.target.value)}
                    maxLength={500}
                    rows={2}
                    aria-label="댓글 수정"
                  />
                  <div className="row">
                    <button className="btn primary" onClick={() => save(c.id!)}>
                      저장
                    </button>
                    <button className="btn" onClick={() => setEditingId(null)}>
                      취소
                    </button>
                  </div>
                </div>
              ) : (
                <p className="comment-text">{c.content}</p>
              )}
              {me?.id === c.userId && editingId !== c.id && (
                <div className="row comment-actions">
                  <button
                    className="link-btn"
                    onClick={() => {
                      setEditingId(c.id!);
                      setEditText(c.content ?? "");
                    }}
                  >
                    수정
                  </button>
                  <button className="link-btn" onClick={() => setDeletingId(c.id!)}>
                    삭제
                  </button>
                </div>
              )}
            </div>
          </li>
        ))}
      </ul>
      <ConfirmDialog
        open={deletingId !== null}
        message="댓글을 삭제할까요?"
        onCancel={() => setDeletingId(null)}
        onConfirm={() => {
          if (deletingId !== null) remove(deletingId);
          setDeletingId(null);
        }}
      />
      {hasMore && (
        <button className="btn more" onClick={() => run(() => load(items[items.length - 1].id))}>
          더 보기
        </button>
      )}
    </section>
  );
}
