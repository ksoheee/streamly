package com.streamly.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamly.support.ApiClient;
import com.streamly.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

// [승인된 인수 테스트] 이 폴더(acceptance/)의 테스트는 정답 기준이다. (STR-9)
// 에이전트는 이 폴더를 수정할 수 없다. 변경하려면 사람이 직접 PR을 올린다.
// 서버 재시작 후 조회는 PostgreSQL 저장으로 보장하고, 실기기 점검(STR-18)에서 확인한다.
@IntegrationTest
class AuthorizationAcceptanceTest {

  @LocalServerPort int port;

  ApiClient anonymous;
  ApiClient owner;
  ApiClient other;
  long broadcastId;
  String streamPath;

  @BeforeEach
  void setUp() {
    anonymous = new ApiClient(port);
    owner = new ApiClient(port);
    other = new ApiClient(port);
    assertThat(owner.signupAndLogin(uniqueName(), "password1234").status()).isEqualTo(200);
    assertThat(other.signupAndLogin(uniqueName(), "password1234").status()).isEqualTo(200);

    ApiClient.Response created =
        owner.post("/api/broadcasts", Map.of("title", "소유자 방송", "description", "설명"));
    assertThat(created.status()).isEqualTo(201);
    broadcastId = created.json().get("id").asLong();
    streamPath = created.json().get("streamPath").asText();
  }

  @Nested
  class 비로그인_사용자는 {

    @Test
    void 방송_목록과_상세를_조회할_수_있다() {
      assertThat(anonymous.get("/api/broadcasts").status()).isEqualTo(200);
      assertThat(anonymous.get("/api/broadcasts/" + broadcastId).status()).isEqualTo(200);
    }

    @Test
    void 방송을_시청할_수_있다() {
      assertThat(hook("read", streamPath, null).status()).isEqualTo(200);
    }

    @Test
    void 방송을_등록할_수_없다() {
      assertThat(anonymous.post("/api/broadcasts", Map.of("title", "x")).status()).isEqualTo(401);
    }

    @Test
    void 방송을_수정_삭제할_수_없다() {
      assertThat(anonymous.put("/api/broadcasts/" + broadcastId, Map.of("title", "x")).status())
          .isEqualTo(401);
      assertThat(anonymous.delete("/api/broadcasts/" + broadcastId).status()).isEqualTo(401);
    }

    @Test
    void 송출을_시작_종료할_수_없다() {
      assertThat(anonymous.post(publishTokenUrl(), null).status()).isEqualTo(401);
      assertThat(anonymous.post(endUrl(), null).status()).isEqualTo(401);
    }
  }

  @Nested
  class 방송_소유자는 {

    @Test
    void 방송을_수정할_수_있다() {
      ApiClient.Response res =
          owner.put("/api/broadcasts/" + broadcastId, Map.of("title", "바뀐 제목"));
      assertThat(res.status()).isEqualTo(200);
      assertThat(res.json().get("title").asText()).isEqualTo("바뀐 제목");
    }

    @Test
    void 방송을_삭제할_수_있다() {
      assertThat(owner.delete("/api/broadcasts/" + broadcastId).status()).isEqualTo(204);
      assertThat(anonymous.get("/api/broadcasts/" + broadcastId).status()).isEqualTo(404);
    }

    @Test
    void 송출_토큰으로_송출하고_종료할_수_있다() {
      String token = issueToken(owner);
      assertThat(hook("publish", streamPath, token).status()).isEqualTo(200);

      ApiClient.Response ended = owner.post(endUrl(), null);
      assertThat(ended.status()).isEqualTo(200);
      assertThat(ended.json().get("status").asText()).isEqualTo("ENDED");
    }
  }

  @Nested
  class 다른_사용자는 {

    @Test
    void 자기_방송은_등록할_수_있다() {
      assertThat(other.post("/api/broadcasts", Map.of("title", "내 방송")).status()).isEqualTo(201);
    }

    @Test
    void 남의_방송을_수정_삭제할_수_없다() {
      assertThat(other.put("/api/broadcasts/" + broadcastId, Map.of("title", "x")).status())
          .isEqualTo(403);
      assertThat(other.delete("/api/broadcasts/" + broadcastId).status()).isEqualTo(403);
    }

    @Test
    void 남의_방송을_송출_시작_종료할_수_없다() {
      assertThat(other.post(publishTokenUrl(), null).status()).isEqualTo(403);
      assertThat(other.post(endUrl(), null).status()).isEqualTo(403);
    }
  }

  @Nested
  class MediaMTX_송출_인증_훅은 {

    @Test
    void 토큰이_없으면_거절한다() {
      assertThat(hook("publish", streamPath, null).status()).isEqualTo(401);
    }

    @Test
    void 남의_방송_토큰이면_거절한다() {
      ApiClient.Response otherBroadcast = other.post("/api/broadcasts", Map.of("title", "남의 방송"));
      long otherId = otherBroadcast.json().get("id").asLong();
      String otherToken =
          other
              .post("/api/broadcasts/" + otherId + "/publish-token", null)
              .json()
              .get("token")
              .asText();

      assertThat(hook("publish", streamPath, otherToken).status()).isEqualTo(401);
    }

    @Test
    void 종료된_방송은_다시_송출할_수_없다() {
      String token = issueToken(owner);
      owner.post(endUrl(), null);

      assertThat(hook("publish", streamPath, token).status()).isEqualTo(401);
      assertThat(owner.post(publishTokenUrl(), null).status()).isEqualTo(409);
    }
  }

  @Test
  void 회원가입_로그인_로그아웃으로_인증_상태가_바뀐다() {
    ApiClient user = new ApiClient(port);
    String name = uniqueName();
    assertThat(user.get("/api/auth/me").status()).isEqualTo(401);

    user.signupAndLogin(name, "password1234");
    ApiClient.Response me = user.get("/api/auth/me");
    assertThat(me.status()).isEqualTo(200);
    assertThat(me.json().get("username").asText()).isEqualTo(name);

    assertThat(user.post("/api/auth/logout", null).status()).isEqualTo(204);
    assertThat(user.get("/api/auth/me").status()).isEqualTo(401);
  }

  @Test
  void 테스트_계정으로_바로_로그인할_수_있다() {
    ApiClient judge = new ApiClient(port);
    assertThat(judge.post("/api/auth/demo-login", Map.of("account", "owner")).status())
        .isEqualTo(200);
    assertThat(judge.get("/api/auth/me").status()).isEqualTo(200);
  }

  private String issueToken(ApiClient client) {
    ApiClient.Response res = client.post(publishTokenUrl(), null);
    assertThat(res.status()).isEqualTo(200);
    return res.json().get("token").asText();
  }

  private ApiClient.Response hook(String action, String path, String token) {
    Map<String, Object> body =
        token == null
            ? Map.of("action", action, "path", path, "protocol", "webrtc")
            : Map.of("action", action, "path", path, "protocol", "webrtc", "token", token);
    return anonymous.post("/internal/mediamtx/auth", body);
  }

  private String publishTokenUrl() {
    return "/api/broadcasts/" + broadcastId + "/publish-token";
  }

  private String endUrl() {
    return "/api/broadcasts/" + broadcastId + "/end";
  }

  private static String uniqueName() {
    return "u" + UUID.randomUUID().toString().substring(0, 8);
  }
}
