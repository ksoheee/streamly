"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";

// 토스 결제창이 결제 후 돌려보내는 곳. 서버에 승인을 요청해야 후원이 확정된다.
export default function DonationSuccessPage() {
  const q = useSearchParams();
  const [state, setState] = useState<"confirming" | "done" | "error">("confirming");
  const [message, setMessage] = useState("");
  const sent = useRef(false);
  const broadcastId = q.get("broadcastId");

  useEffect(() => {
    if (sent.current) return;
    sent.current = true;
    api("POST", "/api/donations/confirm", {
      paymentKey: q.get("paymentKey"),
      orderId: q.get("orderId"),
      amount: Number(q.get("amount")),
    })
      .then(() => setState("done"))
      .catch((e) => {
        setState("error");
        setMessage((e as Error).message);
      });
  }, [q]);

  return (
    <div className="narrow">
      <h1>{state === "confirming" ? "후원 확인 중…" : state === "done" ? "후원 완료" : "후원 실패"}</h1>
      {state === "done" && <p>{Number(q.get("amount")).toLocaleString("ko-KR")}원 후원이 완료됐어요. 고마워요!</p>}
      {state === "error" && <p className="error">{message}</p>}
      {broadcastId && state !== "confirming" && (
        <Link href={`/broadcasts/${broadcastId}`} className="btn primary">
          방송으로 돌아가기
        </Link>
      )}
    </div>
  );
}
