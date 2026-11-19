package com.streamly.comment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamly.support.ApiClient;
import com.streamly.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

// [선택 기능] 댓글: 로그인 사용자 작성, 누구나 조회, 작성자만 수정·삭제 (STR-22)
@IntegrationTest
class CommentTest {

  @LocalServerPort int port;

  ApiClient anonymous;
  ApiClient writer;
  ApiClient other;
  long broadcastId;

  @BeforeEach
  void setUp() {
    anonymous = new ApiClient(port);
    writer = new ApiClient(port);
    other = new ApiClient(port);
    writer.signupAndLogin(name(), "password1234");
    other.signupAndLogin(name(), "password1234");
    broadcastId =
        writer.post("/api/broadcasts", Map.of("title", "댓글 방송")).json().get("id").asLong();
  }

  @Test
  void 로그인한_사용자가_작성하고_누구나_최신순으로_조회한다() {
    assertThat(writer.post(comments(), Map.of("content", "첫 댓글")).status()).isEqualTo(201);
    assertThat(other.post(comments(), Map.of("content", "두번째")).status()).isEqualTo(201);

    JsonNode list = anonymous.get(comments()).json();
    assertThat(list).hasSize(2);
    assertThat(list.get(0).get("content").asText()).isEqualTo("두번째");
  }

  @Test
  void 비로그인은_작성할_수_없고_빈_댓글과_500자_초과는_거절한다() {
    assertThat(anonymous.post(comments(), Map.of("content", "x")).status()).isEqualTo(401);
    assertThat(writer.post(comments(), Map.of("content", " ")).status()).isEqualTo(400);
    assertThat(writer.post(comments(), Map.of("content", "가".repeat(501))).status()).isEqualTo(400);
  }

  @Test
  void 작성자는_수정하고_삭제할_수_있다() {
    long id = writer.post(comments(), Map.of("content", "원래")).json().get("id").asLong();

    ApiClient.Response edited = writer.put("/api/comments/" + id, Map.of("content", "고침"));
    assertThat(edited.status()).isEqualTo(200);
    assertThat(edited.json().get("content").asText()).isEqualTo("고침");
    assertThat(edited.json().get("edited").asBoolean()).isTrue();

    assertThat(writer.delete("/api/comments/" + id).status()).isEqualTo(204);
    assertThat(anonymous.get(comments()).json()).isEmpty();
  }

  @Test
  void 다른_사용자와_비로그인은_수정_삭제할_수_없다() {
    long id = writer.post(comments(), Map.of("content", "내 댓글")).json().get("id").asLong();

    assertThat(other.put("/api/comments/" + id, Map.of("content", "x")).status()).isEqualTo(403);
    assertThat(other.delete("/api/comments/" + id).status()).isEqualTo(403);
    assertThat(anonymous.put("/api/comments/" + id, Map.of("content", "x")).status())
        .isEqualTo(401);
    assertThat(anonymous.delete("/api/comments/" + id).status()).isEqualTo(401);
    assertThat(anonymous.get(comments()).json().get(0).get("content").asText()).isEqualTo("내 댓글");
  }

  @Test
  void 종료된_방송에도_달_수_있고_방송을_지우면_댓글도_사라진다() {
    writer.post("/api/broadcasts/" + broadcastId + "/end", null);
    assertThat(other.post(comments(), Map.of("content", "다시보기 좋아요")).status()).isEqualTo(201);

    writer.delete("/api/broadcasts/" + broadcastId);
    assertThat(anonymous.get(comments()).status()).isEqualTo(404);
  }

  @Test
  void 더_보기는_before보다_오래된_댓글을_20개씩_준다() {
    for (int i = 0; i < 21; i++) {
      writer.post(comments(), Map.of("content", "c" + i));
    }
    JsonNode first = anonymous.get(comments()).json();
    assertThat(first).hasSize(20);
    long oldest = first.get(19).get("id").asLong();
    JsonNode next = anonymous.get(comments() + "?before=" + oldest).json();
    assertThat(next).hasSize(1);
    assertThat(next.get(0).get("content").asText()).isEqualTo("c0");
  }

  private String comments() {
    return "/api/broadcasts/" + broadcastId + "/comments";
  }

  private static String name() {
    return "u" + UUID.randomUUID().toString().substring(0, 8);
  }
}
