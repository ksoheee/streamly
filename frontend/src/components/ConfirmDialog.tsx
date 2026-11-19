"use client";

import { useEffect, useRef } from "react";

// 브라우저 기본 confirm 대신 쓰는 작은 확인창
export default function ConfirmDialog({
  open,
  message,
  confirmLabel = "삭제",
  onConfirm,
  onCancel,
}: {
  open: boolean;
  message: string;
  confirmLabel?: string;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !d.open) d.showModal();
    if (!open && d.open) d.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      className="confirm"
      onCancel={onCancel}
      onClick={(e) => e.target === ref.current && onCancel()}
    >
      <p>{message}</p>
      <div className="row confirm-actions">
        <button className="btn" onClick={onCancel}>
          취소
        </button>
        <button className="btn danger-fill" onClick={onConfirm} autoFocus>
          {confirmLabel}
        </button>
      </div>
    </dialog>
  );
}
