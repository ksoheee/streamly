package com.streamly.comment;

import com.streamly.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

// 방송에 남기는 글. 채팅과 달리 종료된 방송에도 달 수 있고, 작성자만 수정·삭제한다.
@Entity
@Table(name = "comments")
public class Comment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "broadcast_id", nullable = false)
  private Long broadcastId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(nullable = false)
  private String content;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  protected Comment() {}

  public Comment(Long broadcastId, User user, String content) {
    this.broadcastId = broadcastId;
    this.user = user;
    this.content = content;
    this.createdAt = Instant.now();
  }

  public boolean isWrittenBy(Long userId) {
    return user.getId().equals(userId);
  }

  public void edit(String content) {
    this.content = content;
    this.updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public String getContent() {
    return content;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
