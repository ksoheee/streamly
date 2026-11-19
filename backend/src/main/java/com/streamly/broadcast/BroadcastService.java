package com.streamly.broadcast;

import com.streamly.auth.UserRepository;
import com.streamly.broadcast.BroadcastDtos.BroadcastRequest;
import com.streamly.broadcast.BroadcastDtos.BroadcastResponse;
import com.streamly.broadcast.BroadcastDtos.PublishTokenResponse;
import com.streamly.media.MediaServerClient;
import com.streamly.media.PublishTokens;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class BroadcastService {

  private static final Logger log = LoggerFactory.getLogger(BroadcastService.class);

  private final BroadcastRepository broadcasts;
  private final UserRepository users;
  private final PublishTokens tokens;
  private final MediaServerClient mediaServer;

  public BroadcastService(
      BroadcastRepository broadcasts,
      UserRepository users,
      PublishTokens tokens,
      MediaServerClient mediaServer) {
    this.broadcasts = broadcasts;
    this.users = users;
    this.tokens = tokens;
    this.mediaServer = mediaServer;
  }

  @Transactional(readOnly = true)
  public List<BroadcastResponse> list(BroadcastStatus status, String query) {
    String q = query == null || query.isBlank() ? null : "%" + query.strip().toLowerCase() + "%";
    List<Broadcast> found = broadcasts.findAllForList(status, q);
    Map<String, Integer> viewers = viewersIfAnyLive(found);
    return found.stream()
        .map(b -> BroadcastResponse.from(b, viewers.getOrDefault(b.getStreamPath(), 0)))
        .toList();
  }

  @Transactional(readOnly = true)
  public BroadcastResponse get(Long id) {
    Broadcast b = find(id);
    return BroadcastResponse.from(
        b, viewersIfAnyLive(List.of(b)).getOrDefault(b.getStreamPath(), 0));
  }

  private Map<String, Integer> viewersIfAnyLive(List<Broadcast> list) {
    return list.stream().anyMatch(b -> b.getStatus() == BroadcastStatus.LIVE)
        ? mediaServer.viewerCounts()
        : Map.of();
  }

  public BroadcastResponse create(Long userId, BroadcastRequest req) {
    Broadcast b = new Broadcast(users.getReferenceById(userId), req.title(), req.description());
    return BroadcastResponse.from(broadcasts.save(b));
  }

  public BroadcastResponse update(Long userId, Long id, BroadcastRequest req) {
    Broadcast b = findOwned(userId, id);
    b.update(req.title(), req.description());
    return BroadcastResponse.from(b);
  }

  public void delete(Long userId, Long id) {
    Broadcast b = findOwned(userId, id);
    if (b.getStatus() == BroadcastStatus.LIVE) {
      mediaServer.kick(b.getStreamPath());
    }
    tokens.revoke(b.getId());
    broadcasts.delete(b);
  }

  public PublishTokenResponse issuePublishToken(Long userId, Long id) {
    Broadcast b = findOwned(userId, id);
    if (!b.canPublish()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "종료된 방송은 다시 송출할 수 없습니다.");
    }
    PublishTokens.Issued issued = tokens.issue(b.getId(), b.getStreamPath());
    return new PublishTokenResponse(issued.token(), b.getStreamPath(), issued.expiresAt());
  }

  public BroadcastResponse end(Long userId, Long id) {
    Broadcast b = findOwned(userId, id);
    if (b.getStatus() != BroadcastStatus.ENDED) {
      BroadcastStatus before = b.getStatus();
      b.end();
      tokens.revoke(b.getId());
      mediaServer.kick(b.getStreamPath());
      log.info("방송 {} 상태 {} → ENDED (원인: 소유자 종료)", b.getId(), before);
    }
    return BroadcastResponse.from(b);
  }

  private Broadcast find(Long id) {
    return broadcasts
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "방송이 없습니다."));
  }

  private Broadcast findOwned(Long userId, Long id) {
    Broadcast b = find(id);
    if (!b.isOwnedBy(userId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "방송 소유자만 할 수 있습니다.");
    }
    return b;
  }
}
