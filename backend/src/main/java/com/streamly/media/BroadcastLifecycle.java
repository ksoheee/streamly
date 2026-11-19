package com.streamly.media;

import com.streamly.broadcast.Broadcast;
import com.streamly.broadcast.BroadcastRepository;
import com.streamly.broadcast.BroadcastStatus;
import com.streamly.common.StreamlyProperties;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

// MediaMTX가 알려주는 실제 송출 상태로 방송 상태를 바꾼다.
@Service
@Transactional
public class BroadcastLifecycle {

  private static final Logger log = LoggerFactory.getLogger(BroadcastLifecycle.class);

  private final BroadcastRepository broadcasts;
  private final MediaServerClient mediaServer;
  private final StreamlyProperties props;

  public BroadcastLifecycle(
      BroadcastRepository broadcasts, MediaServerClient mediaServer, StreamlyProperties props) {
    this.broadcasts = broadcasts;
    this.mediaServer = mediaServer;
    this.props = props;
  }

  /** 송출이 실제로 시작됐다 (MediaMTX runOnReady). */
  public void onReady(String streamPath) {
    broadcasts
        .findByStreamPath(streamPath)
        .ifPresent(
            b -> {
              if (!b.canPublish()) {
                mediaServer.kick(streamPath);
                return;
              }
              BroadcastStatus before = b.getStatus();
              boolean reconnect = b.getDisconnectedAt() != null;
              b.markLive();
              log.info(
                  "방송 {} 상태 {} → LIVE (원인: {})",
                  b.getId(),
                  before,
                  reconnect ? "유예 중 재연결" : "송출 시작");
            });
  }

  /** 송출이 끊겼다 (MediaMTX runOnNotReady). 바로 끝내지 않고 유예한다. */
  public void onNotReady(String streamPath) {
    broadcasts
        .findByStreamPath(streamPath)
        .filter(b -> b.getStatus() == BroadcastStatus.LIVE)
        .ifPresent(
            b -> {
              b.markDisconnected();
              log.info("방송 {} 송출 끊김, {} 유예 시작", b.getId(), props.reconnectGrace());
            });
  }

  @Scheduled(fixedDelay = 5000)
  public void endExpiredGrace() {
    Instant limit = Instant.now().minus(props.reconnectGrace());
    for (Broadcast b :
        broadcasts.findByStatusAndDisconnectedAtBefore(BroadcastStatus.LIVE, limit)) {
      b.end();
      log.info("방송 {} 상태 LIVE → ENDED (원인: 유예 시간 동안 재연결 없음)", b.getId());
    }
  }

  /** 서버 재시작 후 MediaMTX의 실제 송출 상태와 DB를 맞춘다. */
  @EventListener(ApplicationReadyEvent.class)
  public void reconcile() {
    for (Broadcast b : broadcasts.findByStatus(BroadcastStatus.LIVE)) {
      try {
        if (!mediaServer.isPublishing(b.getStreamPath())) {
          b.markDisconnected();
          log.info("방송 {} 재시작 후 송출 없음, 유예 시작", b.getId());
        }
      } catch (RestClientException e) {
        log.warn("MediaMTX 상태 확인 실패, 방송 {} 상태 유지: {}", b.getId(), e.getMessage());
      }
    }
  }
}
