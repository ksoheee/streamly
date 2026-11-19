package com.streamly.media;

import com.streamly.broadcast.BroadcastRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// MediaMTX → 서버 내부 호출 전용. Caddy에서 /internal/* 외부 접근을 막는다.
@RestController
@RequestMapping("/internal/mediamtx")
public class MediaMtxHookController {

  // MediaMTX authMethod: http 요청 본문
  public record AuthRequest(
      String user,
      String password,
      String token,
      String ip,
      String action,
      String path,
      String protocol,
      String id,
      String query) {}

  public record EventRequest(String type, String path) {}

  private final PublishTokens tokens;
  private final BroadcastRepository broadcasts;
  private final BroadcastLifecycle lifecycle;

  public MediaMtxHookController(
      PublishTokens tokens, BroadcastRepository broadcasts, BroadcastLifecycle lifecycle) {
    this.tokens = tokens;
    this.broadcasts = broadcasts;
    this.lifecycle = lifecycle;
  }

  @PostMapping("/auth")
  public ResponseEntity<Void> auth(@RequestBody AuthRequest req) {
    if (!"publish".equals(req.action())) {
      return ResponseEntity.ok().build(); // 시청(read) 등은 누구나
    }
    String token = req.token() != null && !req.token().isBlank() ? req.token() : req.password();
    if (token != null && token.startsWith("Bearer ")) {
      token = token.substring("Bearer ".length());
    }
    boolean allowed =
        tokens
            .verify(token, req.path())
            .flatMap(i -> broadcasts.findById(i.broadcastId()))
            .filter(b -> b.canPublish())
            .isPresent();
    return allowed ? ResponseEntity.ok().build() : ResponseEntity.status(401).build();
  }

  @PostMapping("/events")
  public ResponseEntity<Void> event(@RequestBody EventRequest req) {
    switch (req.type()) {
      case "ready" -> lifecycle.onReady(req.path());
      case "notready" -> lifecycle.onNotReady(req.path());
      default -> {
        return ResponseEntity.badRequest().build();
      }
    }
    return ResponseEntity.noContent().build();
  }
}
