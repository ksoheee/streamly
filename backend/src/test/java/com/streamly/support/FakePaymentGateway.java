package com.streamly.support;

import com.streamly.donation.PaymentGateway;
import java.util.concurrent.atomic.AtomicInteger;

// 테스트에서 토스 대신 쓰는 가짜. 승인 요청을 세고, 결과를 바꿔 끼울 수 있다.
public class FakePaymentGateway implements PaymentGateway {

  public final AtomicInteger calls = new AtomicInteger();
  public volatile Long overrideAmount;

  @Override
  public Approval confirm(String paymentKey, String orderId, long amount) {
    calls.incrementAndGet();
    long total = overrideAmount != null ? overrideAmount : amount;
    return new Approval(paymentKey, orderId, total, "DONE");
  }
}
