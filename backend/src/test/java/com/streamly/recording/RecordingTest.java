package com.streamly.recording;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamly.support.ApiClient;
import com.streamly.support.IntegrationTest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

// [선택 기능] 녹화·다시보기 (STR-23)
@IntegrationTest
class RecordingTest {

  @TempDir static Path dir;

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("streamly.recordings-dir", () -> dir.toString());
  }

  @LocalServerPort int port;

  ApiClient owner;
  ApiClient other;
  ApiClient anonymous;
  long broadcastId;
  String streamPath;

  @BeforeEach
  void setUp() {
    owner = new ApiClient(port);
    other = new ApiClient(port);
    anonymous = new ApiClient(port);
    owner.signupAndLogin(name(), "password1234");
    other.signupAndLogin(name(), "password1234");
    JsonNode b = owner.post("/api/broadcasts", Map.of("title", "녹화 방송")).json();
    broadcastId = b.get("id").asLong();
    streamPath = b.get("streamPath").asText();
  }

  @Test
  void 녹화가_끝나면_등록되고_누구나_목록_상세_재생할_수_있다() throws Exception {
    long id = completeSegment("0123456789".repeat(100));

    assertThat(anonymous.get("/api/broadcasts/" + broadcastId + "/recordings").json()).hasSize(1);
    JsonNode detail = anonymous.get("/api/recordings/" + id).json();
    assertThat(detail.get("title").asText()).isEqualTo("녹화 방송");
    assertThat(detail.get("durationSeconds").asDouble()).isEqualTo(62.5);
    assertThat(detail.get("sizeBytes").asLong()).isEqualTo(1000);

    HttpResponse<byte[]> range =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/recordings/" + id + "/video"))
                    .header("Range", "bytes=0-9")
                    .build(),
                HttpResponse.BodyHandlers.ofByteArray());
    assertThat(range.statusCode()).isEqualTo(206);
    assertThat(new String(range.body())).isEqualTo("0123456789");
  }

  @Test
  void 녹화는_방송_소유자만_삭제한다() throws Exception {
    long id = completeSegment("x");
    assertThat(other.delete("/api/recordings/" + id).status()).isEqualTo(403);
    assertThat(anonymous.delete("/api/recordings/" + id).status()).isEqualTo(401);
    assertThat(owner.delete("/api/recordings/" + id).status()).isEqualTo(204);
    assertThat(anonymous.get("/api/recordings/" + id).status()).isEqualTo(404);
  }

  @Test
  void 녹화_폴더_밖을_가리키는_파일은_등록하지_않는다() {
    anonymous.post(
        "/internal/mediamtx/recordings?path=" + streamPath + "&file=/recordings/../../etc/passwd",
        null);
    assertThat(anonymous.get("/api/broadcasts/" + broadcastId + "/recordings").json()).isEmpty();
  }

  @Test
  void Go_duration_문자열을_초로_바꾼다() {
    assertThat(RecordingController.parseSeconds("1h2m3.5s")).isEqualTo(3723.5);
    assertThat(RecordingController.parseSeconds("12.25")).isEqualTo(12.25);
  }

  private long completeSegment(String content) throws IOException {
    String rel = streamPath + "/" + UUID.randomUUID() + ".mp4";
    Files.createDirectories(dir.resolve(streamPath));
    Files.writeString(dir.resolve(rel), content);
    anonymous.post(
        "/internal/mediamtx/recordings?path="
            + streamPath
            + "&file=/recordings/"
            + rel
            + "&duration=1m2.5s",
        null);
    return anonymous
        .get("/api/broadcasts/" + broadcastId + "/recordings")
        .json()
        .get(0)
        .get("id")
        .asLong();
  }

  private static String name() {
    return "u" + UUID.randomUUID().toString().substring(0, 8);
  }
}
