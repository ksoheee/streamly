// 토스페이먼츠 결제창 SDK v2 (스크립트로 불러온다)
type TossPayment = {
  requestPayment(o: {
    method: "CARD";
    amount: { currency: "KRW"; value: number };
    orderId: string;
    orderName: string;
    successUrl: string;
    failUrl: string;
  }): Promise<void>;
};
type TossPaymentsFn = (clientKey: string) => { payment(o: { customerKey: string }): TossPayment };

declare global {
  interface Window {
    TossPayments?: TossPaymentsFn;
  }
}

let loading: Promise<TossPaymentsFn> | null = null;

export function loadTossPayments(): Promise<TossPaymentsFn> {
  if (window.TossPayments) return Promise.resolve(window.TossPayments);
  loading ??= new Promise((resolve, reject) => {
    const s = document.createElement("script");
    s.src = "https://js.tosspayments.com/v2/standard";
    s.onload = () => (window.TossPayments ? resolve(window.TossPayments) : reject(new Error("결제 모듈을 불러오지 못했습니다.")));
    s.onerror = () => {
      loading = null;
      reject(new Error("결제 모듈을 불러오지 못했습니다."));
    };
    document.head.appendChild(s);
  });
  return loading;
}
