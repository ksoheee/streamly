package com.streamly.broadcast;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class BroadcastDtos {
  private BroadcastDtos() {}

  public record BroadcastRequest(
      @NotBlank @Size(max = 100) String title, @Size(max = 1000) String description) {}

  public record BroadcastResponse(
      Long id,
      String title,
      String description,
      BroadcastStatus status,
      boolean reconnecting,
      Long ownerId,
      String ownerName,
      String streamPath,
      Instant createdAt,
      Instant startedAt,
      Instant endedAt) {

    static BroadcastResponse from(Broadcast b) {
      return new BroadcastResponse(
          b.getId(),
          b.getTitle(),
          b.getDescription(),
          b.getStatus(),
          b.getDisconnectedAt() != null,
          b.getOwner().getId(),
          b.getOwner().getUsername(),
          b.getStreamPath(),
          b.getCreatedAt(),
          b.getStartedAt(),
          b.getEndedAt());
    }
  }

  public record PublishTokenResponse(String token, String streamPath, Instant expiresAt) {}
}
