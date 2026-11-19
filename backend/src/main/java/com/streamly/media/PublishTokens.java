package com.streamly.media;

import com.streamly.common.StreamlyProperties;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

// 송출 토큰. 방송 1개에만 유효하고 짧게 만료된다.
// shortcut: 서버 메모리에 둔다 (앱 1대 운영). 재시작하면 토큰이 사라지므로 소유자가 다시 발급받는다.
@Component
public class PublishTokens {

  public record Issued(String token, Long broadcastId, String streamPath, Instant expiresAt) {}

  private final SecureRandom random = new SecureRandom();
  private final Map<String, Issued> byToken = new ConcurrentHashMap<>();
  private final StreamlyProperties props;

  public PublishTokens(StreamlyProperties props) {
    this.props = props;
  }

  public Issued issue(Long broadcastId, String streamPath) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Issued issued =
        new Issued(token, broadcastId, streamPath, Instant.now().plus(props.publishTokenTtl()));
    byToken.values().removeIf(i -> i.expiresAt().isBefore(Instant.now()));
    byToken.put(token, issued);
    return issued;
  }

  /** 토큰이 이 경로에 대해 유효하면 돌려준다. */
  public Optional<Issued> verify(String token, String streamPath) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(byToken.get(token))
        .filter(i -> i.streamPath().equals(streamPath))
        .filter(i -> i.expiresAt().isAfter(Instant.now()));
  }

  public void revoke(Long broadcastId) {
    byToken.values().removeIf(i -> i.broadcastId().equals(broadcastId));
  }
}
