package com.streamly.donation;

import com.streamly.auth.User;
import com.streamly.broadcast.Broadcast;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

// 후원 1건. 금액·받는 사람은 주문을 만들 때 서버가 정하고, 결제 승인 때 그 값과 대조한다.
@Entity
@Table(name = "donations")
public class Donation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "broadcast_id")
  private Broadcast broadcast;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "donor_id")
  private User donor;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "recipient_id")
  private User recipient;

  @Column(name = "order_id", nullable = false, unique = true)
  private String orderId;

  @Column(nullable = false)
  private long amount;

  private String message;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DonationStatus status;

  @Column(name = "payment_key", unique = true)
  private String paymentKey;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "paid_at")
  private Instant paidAt;

  protected Donation() {}

  public Donation(Broadcast broadcast, User donor, long amount, String message) {
    this.broadcast = broadcast;
    this.donor = donor;
    this.recipient = broadcast.getOwner();
    this.orderId = "don_" + UUID.randomUUID().toString().replace("-", "");
    this.amount = amount;
    this.message = message;
    this.status = DonationStatus.PENDING;
    this.createdAt = Instant.now();
  }

  public void markPaid(String paymentKey) {
    this.status = DonationStatus.PAID;
    this.paymentKey = paymentKey;
    this.paidAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public Broadcast getBroadcast() {
    return broadcast;
  }

  public User getDonor() {
    return donor;
  }

  public User getRecipient() {
    return recipient;
  }

  public String getOrderId() {
    return orderId;
  }

  public long getAmount() {
    return amount;
  }

  public String getMessage() {
    return message;
  }

  public DonationStatus getStatus() {
    return status;
  }

  public String getPaymentKey() {
    return paymentKey;
  }

  public Instant getPaidAt() {
    return paidAt;
  }
}
