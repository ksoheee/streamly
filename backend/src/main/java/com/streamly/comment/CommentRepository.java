package com.streamly.comment;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CommentRepository extends JpaRepository<Comment, Long> {

  // 최신순. beforeId 보다 오래된 것만 (더 보기)
  @Query(
      "select c from Comment c join fetch c.user"
          + " where c.broadcastId = :broadcastId and c.id < :beforeId order by c.id desc")
  List<Comment> findPage(Long broadcastId, long beforeId, Limit limit);
}
