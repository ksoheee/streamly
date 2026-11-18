package com.streamly.broadcast;

import com.streamly.auth.User;
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

// 방송 1건 = 송출 1회. READY → LIVE → ENDED 로만 바뀐다.
// LIVE 중 송출이 끊기면 disconnectedAt 을 기록하고, 유예 시간 안에 다시 연결되지 않으면 ENDED.
@Entity
@Table(name = "broadcasts")
public class Broadcast {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "owner_id")
  private User owner;

  @Column(nullable = false)
  private String title;

  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private BroadcastStatus status;

  @Column(name = "stream_path", nullable = false, unique = true)
  private String streamPath;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  @Column(name = "disconnected_at")
  private Instant disconnectedAt;

  protected Broadcast() {}

  public Broadcast(User owner, String title, String description) {
    this.owner = owner;
    this.title = title;
    this.description = description;
    this.status = BroadcastStatus.READY;
    this.streamPath = UUID.randomUUID().toString().replace("-", ""); // 추측 불가능한 경로
    this.createdAt = Instant.now();
  }

  public boolean isOwnedBy(Long userId) {
    return owner.getId().equals(userId);
  }

  public boolean canPublish() {
    return status != BroadcastStatus.ENDED;
  }

  public void update(String title, String description) {
    this.title = title;
    this.description = description;
  }

  /** 송출이 실제로 시작(또는 유예 중 재연결)됐다. */
  public void markLive() {
    if (startedAt == null) {
      startedAt = Instant.now();
    }
    status = BroadcastStatus.LIVE;
    disconnectedAt = null;
  }

  /** 송출이 끊겼다. 유예 시간 동안 LIVE를 유지한다. */
  public void markDisconnected() {
    if (status == BroadcastStatus.LIVE && disconnectedAt == null) {
      disconnectedAt = Instant.now();
    }
  }

  public void end() {
    status = BroadcastStatus.ENDED;
    endedAt = Instant.now();
    disconnectedAt = null;
  }

  public Long getId() {
    return id;
  }

  public User getOwner() {
    return owner;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public BroadcastStatus getStatus() {
    return status;
  }

  public String getStreamPath() {
    return streamPath;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getEndedAt() {
    return endedAt;
  }

  public Instant getDisconnectedAt() {
    return disconnectedAt;
  }
}
