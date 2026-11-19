"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { api, type Broadcast, type DonationOrder, type DonationSummary } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { loadTossPayments } from "@/lib/toss";

const PRESETS = [1000, 5000, 10000, 50000];
const won = (n: number | undefined) => `${(n ?? 0).toLocaleString("ko-KR")}원`;

// 방송자에게 후원 (토스페이먼츠 테스트 결제). 결제가 끝나면 /donations/success 에서 서버 승인.
export default function Donations({ b }: { b: Broadcast }) {
  const { me } = useAuth();
  const [summary, setSummary] = useState<DonationSummary | null>(null);
  const [open, setOpen] = useState(false);
  const [amount, setAmount] = useState(5000);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(
    () =>
      api<DonationSummary>("GET", `/api/broadcasts/${b.id}/donations`)
        .then(setSummary)
        .catch(() => {}),
    [b.id],
  );

  useEffect(() => {
    load();
    const t = setInterval(load, 10000);
    return () => clearInterval(t);
  }, [load]);

  async function pay() {
    setBusy(true);
    setError("");
    try {
      const order = await api<DonationOrder>("POST", `/api/broadcasts/${b.id}/donations`, { amount, message });
      if (!order.clientKey) throw new Error("결제 키가 설정되지 않아 후원할 수 없습니다.");
      const TossPayments = await loadTossPayments();
      const back = `${location.origin}/donations`;
      await TossPayments(order.clientKey)
        .payment({ customerKey: order.customerKey! })
        .requestPayment({
          method: "CARD",
          amount: { currency: "KRW", value: order.amount! },
          orderId: order.orderId!,
          orderName: order.orderName!,
          successUrl: `${back}/success?broadcastId=${b.id}`,
          failUrl: `${back}/fail?broadcastId=${b.id}`,
        });
    } catch (e) {
      setError((e as Error).message);
      setBusy(false);
    }
  }

  const canDonate = b.status !== "ENDED" && me?.id !== b.ownerId;

  return (
    <section className="donations" aria-label="후원">
      <div className="row between">
        <h2>
          후원 <span className="donation-total">{won(summary?.total)}</span>
        </h2>
        {canDonate &&
          (me ? (
            <button className="btn primary" onClick={() => setOpen((v) => !v)}>
              {open ? "닫기" : "후원하기"}
            </button>
          ) : (
            <Link href="/login" className="btn">
              로그인하고 후원하기
            </Link>
          ))}
      </div>

      {open && canDonate && me && (
        <div className="donation-form">
          <p className="muted">{b.ownerName}님에게 후원해요. 테스트 결제라 실제로 돈이 나가지 않아요.</p>
          <div className="row">
            {PRESETS.map((p) => (
              <button
                key={p}
                className={`tab ${amount === p ? "active" : ""}`}
                onClick={() => setAmount(p)}
                type="button"
              >
                {won(p)}
              </button>
            ))}
          </div>
          <label className="row">
            직접 입력
            <input
              type="number"
              min={1000}
              max={100000}
              step={100}
              value={amount}
              onChange={(e) => setAmount(Number(e.target.value))}
              aria-label="후원 금액"
            />
            원
          </label>
          <input
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            maxLength={100}
            placeholder="응원 메시지 (선택, 100자)"
            aria-label="응원 메시지"
          />
          <button className="btn primary" disabled={busy} onClick={pay}>
            {won(amount)} 결제하기
          </button>
          {error && <p className="error">{error}</p>}
        </div>
      )}

      {summary && summary.donations && summary.donations.length > 0 && (
        <ul className="donation-list">
          {summary.donations.slice(0, 10).map((d) => (
            <li key={d.id}>
              <strong>{d.donorName}</strong> <span className="donation-amount">{won(d.amount)}</span>
              {d.message && <span className="muted"> · {d.message}</span>}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
