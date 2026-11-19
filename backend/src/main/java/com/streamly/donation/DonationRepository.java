package com.streamly.donation;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface DonationRepository extends JpaRepository<Donation, Long> {

  // 같은 주문의 승인 요청이 동시에 와도 한 번만 처리하도록 행을 잠근다
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from Donation d where d.orderId = :orderId")
  Optional<Donation> findByOrderIdForUpdate(String orderId);

  @Query(
      "select d from Donation d join fetch d.donor where d.broadcast.id = :broadcastId"
          + " and d.status = com.streamly.donation.DonationStatus.PAID order by d.paidAt desc")
  List<Donation> findPaid(Long broadcastId);

  @Query(
      "select coalesce(sum(d.amount), 0) from Donation d where d.broadcast.id = :broadcastId"
          + " and d.status = com.streamly.donation.DonationStatus.PAID")
  long sumPaid(Long broadcastId);
}
