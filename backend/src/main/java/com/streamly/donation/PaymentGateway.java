package com.streamly.donation;

// 결제 승인 API. 운영은 토스페이먼츠, 테스트는 가짜 구현.
public interface PaymentGateway {

  record Approval(String paymentKey, String orderId, long totalAmount, String status) {}

  /** 결제를 승인하고 결제사가 돌려준 결과를 준다. 실패하면 PaymentException. */
  Approval confirm(String paymentKey, String orderId, long amount);

  class PaymentException extends RuntimeException {
    public PaymentException(String message) {
      super(message);
    }
  }
}
