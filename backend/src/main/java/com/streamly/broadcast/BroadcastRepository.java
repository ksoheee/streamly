package com.streamly.broadcast;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BroadcastRepository extends JpaRepository<Broadcast, Long> {

  // LIVE 먼저, 그다음 최신순
  @Query(
      "select b from Broadcast b join fetch b.owner"
          + " where (:status is null or b.status = :status)"
          + " and (:q is null or lower(b.title) like :q or lower(b.owner.username) like :q)"
          + " order by case when b.status = com.streamly.broadcast.BroadcastStatus.LIVE then 0 else 1 end,"
          + " b.createdAt desc")
  List<Broadcast> findAllForList(BroadcastStatus status, String q);

  Optional<Broadcast> findByStreamPath(String streamPath);

  List<Broadcast> findByStatus(BroadcastStatus status);

  List<Broadcast> findByStatusAndDisconnectedAtBefore(BroadcastStatus status, Instant before);
}
