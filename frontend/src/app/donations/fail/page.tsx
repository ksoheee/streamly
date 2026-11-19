"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";

export default function DonationFailPage() {
  const q = useSearchParams();
  const broadcastId = q.get("broadcastId");
  return (
    <div className="narrow">
      <h1>후원이 취소됐어요</h1>
      <p className="muted">{q.get("message") ?? "결제가 완료되지 않았어요."}</p>
      {broadcastId && (
        <Link href={`/broadcasts/${broadcastId}`} className="btn primary">
          방송으로 돌아가기
        </Link>
      )}
    </div>
  );
}
