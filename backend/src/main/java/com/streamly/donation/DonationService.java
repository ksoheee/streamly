package com.streamly.donation;

import com.streamly.auth.UserRepository;
import com.streamly.broadcast.Broadcast;
import com.streamly.broadcast.BroadcastRepository;
import com.streamly.broadcast.BroadcastStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class DonationService {

  private static final Logger log = LoggerFactory.getLogger(DonationService.class);
  static final long MIN = 1_000;
  static final long MAX = 100_000;

  private final DonationRepository donations;
  private final BroadcastRepository broadcasts;
  private final UserRepository users;
  private final PaymentGateway gateway;

  public DonationService(
      DonationRepository donations,
      BroadcastRepository broadcasts,
      UserRepository users,
      PaymentGateway gateway) {
    this.donations = donations;
    this.broadcasts = broadcasts;
    this.users = users;
    this.gateway = gateway;
  }

  /** 결제창을 열기 전에 주문을 만든다. 받는 사람은 방송 소유자로 서버가 정한다. */
  public Donation order(Long userId, Long broadcastId, long amount, String message) {
    Broadcast b =
        broadcasts
            .findById(broadcastId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "방송이 없습니다."));
    if (b.getStatus() == BroadcastStatus.ENDED) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "종료된 방송에는 후원할 수 없습니다.");
    }
    if (b.isOwnedBy(userId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 방송에는 후원할 수 없습니다.");
    }
    if (amount < MIN || amount > MAX || amount % 100 != 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "후원 금액은 1,000원 ~ 100,000원, 100원 단위입니다.");
    }
    String msg = message == null || message.isBlank() ? null : message.strip();
    return donations.save(new Donation(b, users.getReferenceById(userId), amount, msg));
  }

  /** 결제창에서 돌아오면 승인한다. 주문 금액과 다르면 승인하지 않고, 이미 승인된 주문이면 다시 적립하지 않는다. */
  public Donation confirm(Long userId, String paymentKey, String orderId, long amount) {
    Donation d =
        donations
            .findByOrderIdForUpdate(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "주문이 없습니다."));
    if (!d.getDonor().getId().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 주문만 승인할 수 있습니다.");
    }
    if (d.getStatus() == DonationStatus.PAID) {
      if (paymentKey.equals(d.getPaymentKey())) {
        return d; // 같은 승인 통지가 다시 와도 한 번만 적립
      }
      throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 결제된 주문입니다.");
    }
    if (d.getAmount() != amount) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "주문 금액과 결제 금액이 다릅니다.");
    }
    PaymentGateway.Approval a;
    try {
      a = gateway.confirm(paymentKey, orderId, amount);
    } catch (PaymentGateway.PaymentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
    }
    // 결제사가 돌려준 값도 주문과 대조한다
    if (!"DONE".equals(a.status())
        || a.totalAmount() != d.getAmount()
        || !orderId.equals(a.orderId())) {
      log.warn("결제 승인 결과 불일치: 주문 {} 결과 {}", orderId, a);
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "결제 승인 결과가 주문과 다릅니다.");
    }
    d.markPaid(a.paymentKey());
    log.info("후원 {}원 승인: 방송 {} 주문 {}", d.getAmount(), d.getBroadcast().getId(), orderId);
    return d;
  }
}
