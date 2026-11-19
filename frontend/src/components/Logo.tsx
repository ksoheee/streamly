import { Pacifico } from "next/font/google";

const pacifico = Pacifico({ weight: "400", subsets: ["latin"], display: "swap" });

// 손글씨 S 아이콘 (좁은 화면, 파비콘과 같은 모양)
export function LogoMark({ size = 28 }: { size?: number }) {
  return (
    <svg className="logo-mark" width={size} height={size} viewBox="0 0 32 32" aria-hidden="true">
      <rect width="32" height="32" rx="9" fill="#FF3E8A" />
      <path d="M21.5 9.5c-2.5-2.5-9-2-9 2s8.5 3.5 8 8-7.5 4.5-10.5 1.5" stroke="#fff" strokeWidth="3" fill="none" strokeLinecap="round" />
    </svg>
  );
}

// 손글씨 워드마크 + 펜으로 그은 밑줄
export default function Logo() {
  return (
    <span className="logo-inner">
      <LogoMark />
      <span className={`logo-text ${pacifico.className}`}>
        Stream<span className="logo-accent">ly</span>
        <svg className="logo-swash" viewBox="0 0 130 12" preserveAspectRatio="none" aria-hidden="true">
          <path d="M4 8C40 2 90 2 126 6" stroke="currentColor" strokeWidth="3" fill="none" strokeLinecap="round" />
        </svg>
      </span>
    </span>
  );
}
