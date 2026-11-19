// 재생 버튼 + 송출 신호(전파) 모양 아이콘과 워드마크
export function LogoMark({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true">
      <rect width="32" height="32" rx="9" fill="#FF3E8A" />
      <path d="M11 9.5v13l10-6.5z" fill="#fff" />
      <path d="M23.5 10.5a8 8 0 0 1 0 11" stroke="#fff" strokeWidth="2.2" fill="none" strokeLinecap="round" />
    </svg>
  );
}

export default function Logo() {
  return (
    <span className="logo-inner">
      <LogoMark />
      <span className="logo-text">
        Stream<span className="logo-accent">ly</span>
      </span>
    </span>
  );
}
