import type { Broadcast } from "@/lib/api";

export default function StatusBadge({ b }: { b: Broadcast }) {
  if (b.status === "LIVE") {
    return <span className="badge live">{b.reconnecting ? "재연결 중" : "● LIVE"}</span>;
  }
  if (b.status === "ENDED") return <span className="badge ended">종료</span>;
  return <span className="badge ready">방송 전</span>;
}
