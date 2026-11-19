package com.streamly.recording;

import com.streamly.broadcast.Broadcast;
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

// MediaMTX가 저장한 녹화 파일 1개. filePath 는 녹화 폴더 기준 상대 경로.
@Entity
@Table(name = "recordings")
public class Recording {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "broadcast_id")
  private Broadcast broadcast;

  @Column(name = "file_path", nullable = false, unique = true)
  private String filePath;

  @Column(name = "duration_seconds", nullable = false)
  private double durationSeconds;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Recording() {}

  public Recording(Broadcast broadcast, String filePath, double durationSeconds, long sizeBytes) {
    this.broadcast = broadcast;
    this.filePath = filePath;
    this.durationSeconds = durationSeconds;
    this.sizeBytes = sizeBytes;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public Broadcast getBroadcast() {
    return broadcast;
  }

  public String getFilePath() {
    return filePath;
  }

  public double getDurationSeconds() {
    return durationSeconds;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
