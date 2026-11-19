package com.streamly.chat;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChatRepository extends JpaRepository<ChatMessage, Long> {

  @Query(
      "select m from ChatMessage m join fetch m.user"
          + " where m.broadcastId = :broadcastId and m.id > :afterId order by m.id desc")
  List<ChatMessage> findRecent(Long broadcastId, long afterId, Limit limit);
}
