package com.streamly.broadcast;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamly.auth.User;
import org.junit.jupiter.api.Test;

class BroadcastTest {

  Broadcast broadcast = new Broadcast(new User("owner", "hash"), "제목", null);

  @Test
  void 처음에는_READY이고_송출할_수_있다() {
    assertThat(broadcast.getStatus()).isEqualTo(BroadcastStatus.READY);
    assertThat(broadcast.canPublish()).isTrue();
  }

  @Test
  void 끊겨도_유예_중에는_LIVE를_유지하고_재연결하면_이어간다() {
    broadcast.markLive();
    broadcast.markDisconnected();
    assertThat(broadcast.getStatus()).isEqualTo(BroadcastStatus.LIVE);
    assertThat(broadcast.getDisconnectedAt()).isNotNull();

    broadcast.markLive();
    assertThat(broadcast.getDisconnectedAt()).isNull();
  }

  @Test
  void 종료되면_다시_송출할_수_없다() {
    broadcast.markLive();
    broadcast.end();
    assertThat(broadcast.getStatus()).isEqualTo(BroadcastStatus.ENDED);
    assertThat(broadcast.canPublish()).isFalse();
  }

  @Test
  void READY에서_끊김_신호는_무시한다() {
    broadcast.markDisconnected();
    assertThat(broadcast.getDisconnectedAt()).isNull();
  }
}
