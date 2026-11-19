package com.streamly.broadcast;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamly.support.ApiClient;
import com.streamly.support.IntegrationTest;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

// 방송 썸네일: 소유자만 올리고 누구나 본다 (STR-27)
@IntegrationTest
class ThumbnailTest {

  static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

  @LocalServerPort int port;

  long id;
  ApiClient anonymous;

  @BeforeEach
  void setUp() {
    anonymous = new ApiClient(port);
  }

  @Test
  void 소유자가_올리면_누구나_보고_목록에_갱신시각이_생긴다() throws Exception {
    Session owner = login();
    id = owner.api.post("/api/broadcasts", Map.of("title", "썸네일")).json().get("id").asLong();
    assertThat(anonymous.get("/api/broadcasts/" + id).json().get("thumbnailUpdatedAt").isNull())
        .isTrue();

    assertThat(owner.put(id, JPEG)).isEqualTo(204);

    HttpResponse<byte[]> img =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(
                        URI.create(
                            "http://localhost:" + port + "/api/broadcasts/" + id + "/thumbnail"))
                    .build(),
                HttpResponse.BodyHandlers.ofByteArray());
    assertThat(img.statusCode()).isEqualTo(200);
    assertThat(img.body()).isEqualTo(JPEG);
    assertThat(anonymous.get("/api/broadcasts/" + id).json().get("thumbnailUpdatedAt").isNull())
        .isFalse();
  }

  @Test
  void 다른_사용자는_올릴_수_없고_JPEG가_아니면_거절한다() throws Exception {
    Session owner = login();
    Session other = login();
    id = owner.api.post("/api/broadcasts", Map.of("title", "썸네일")).json().get("id").asLong();

    assertThat(other.put(id, JPEG)).isEqualTo(403);
    assertThat(owner.put(id, "not a jpeg".getBytes())).isEqualTo(400);
    assertThat(owner.put(id, new byte[ThumbnailController.MAX_BYTES + 1])).isEqualTo(400);
    assertThat(anonymous.get("/api/broadcasts/" + id + "/thumbnail").status()).isEqualTo(404);
  }

  // ApiClient 는 JSON 만 보내서, 이미지 업로드는 같은 쿠키로 직접 보낸다
  record Session(ApiClient api, CookieManager cookies, HttpClient http, int port) {
    int put(long id, byte[] body) throws Exception {
      String xsrf =
          cookies.getCookieStore().getCookies().stream()
              .filter(c -> c.getName().equals("XSRF-TOKEN"))
              .map(HttpCookie::getValue)
              .findFirst()
              .orElseThrow();
      return http.send(
              HttpRequest.newBuilder(
                      URI.create(
                          "http://localhost:" + port + "/api/broadcasts/" + id + "/thumbnail"))
                  .header("Content-Type", "image/jpeg")
                  .header("X-XSRF-TOKEN", xsrf)
                  .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                  .build(),
              HttpResponse.BodyHandlers.discarding())
          .statusCode();
    }
  }

  Session login() {
    ApiClient api = new ApiClient(port);
    api.signupAndLogin("u" + UUID.randomUUID().toString().substring(0, 8), "password1234");
    return new Session(api, api.cookies(), api.http(), port);
  }
}
