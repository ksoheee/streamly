package com.streamly.recording;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RecordingRepository extends JpaRepository<Recording, Long> {

  @Query("select r from Recording r join fetch r.broadcast b join fetch b.owner order by r.id desc")
  List<Recording> findAllNewest();

  @Query(
      "select r from Recording r join fetch r.broadcast b join fetch b.owner"
          + " where b.id = :broadcastId order by r.id")
  List<Recording> findByBroadcast(Long broadcastId);

  boolean existsByFilePath(String filePath);

  List<Recording> findByCreatedAtBefore(Instant before);
}
