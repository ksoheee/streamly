import Link from "next/link";
import type { Broadcast } from "@/lib/api";
import { elapsed } from "@/lib/time";

export default function BroadcastCard({ b, now }: { b: Broadcast; now: number }) {
  const live = b.status === "LIVE";
  return (
    <Link href={`/broadcasts/${b.id}`} className="bcard">
      <div className={`thumb ${b.status === "ENDED" ? "ended" : ""}`}>
        {b.thumbnailUpdatedAt ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            className="thumb-img"
            src={`/api/broadcasts/${b.id}/thumbnail?v=${encodeURIComponent(b.thumbnailUpdatedAt)}`}
            alt=""
            loading="lazy"
          />
        ) : (
          <span className="thumb-initial">{b.title?.slice(0, 1)}</span>
        )}
        {live ? (
          <>
            <span className="pill live">{b.reconnecting ? "재연결 중" : "LIVE"}</span>
            <span className="pill dim viewers">{b.viewerCount ?? 0}명</span>
            <span className="pill dim time">{elapsed(b.startedAt, now)}</span>
          </>
        ) : (
          <span className="pill dim">{b.status === "ENDED" ? "종료됨" : "방송 전"}</span>
        )}
      </div>
      <div className="bcard-info">
        <span className="avatar">{b.ownerName?.slice(0, 1).toUpperCase()}</span>
        <div>
          <strong className="bcard-title">{b.title}</strong>
          <span className="muted">{b.ownerName}</span>
        </div>
      </div>
    </Link>
  );
}
