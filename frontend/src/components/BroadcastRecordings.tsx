"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api, type Recording } from "@/lib/api";
import { duration } from "@/lib/time";

// 종료된 방송 화면에서 그 방송의 녹화로 가는 링크
export default function BroadcastRecordings({ broadcastId }: { broadcastId: number }) {
  const [items, setItems] = useState<Recording[]>([]);

  useEffect(() => {
    api<Recording[]>("GET", `/api/broadcasts/${broadcastId}/recordings`)
      .then(setItems)
      .catch(() => {});
  }, [broadcastId]);

  if (items.length === 0) return null;
  return (
    <div className="row recordings-row">
      <strong>다시보기</strong>
      {items.map((r, i) => (
        <Link key={r.id} href={`/recordings/${r.id}`} className="btn primary">
          ▶ {items.length > 1 ? `${i + 1}부 ` : ""}
          {duration(r.durationSeconds)}
        </Link>
      ))}
    </div>
  );
}
