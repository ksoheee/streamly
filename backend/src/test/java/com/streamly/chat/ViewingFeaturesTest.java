package com.streamly.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamly.broadcast.BroadcastRepository;
import com.streamly.media.MediaServerClient;
import com.streamly.support.ApiClient;
import com.streamly.support.FakeMediaServerClient;
import com.streamly.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

// 시청 화면 기능: 채팅, 검색, 시청자 수
@IntegrationTest
class ViewingFeaturesTest {

  @LocalServerPort int port;
  @Autowired MediaServerClient mediaServer;
  @Autowired BroadcastRepository broadcasts;

  ApiClient anonymous;
  ApiClient owner;
  String ownerName;
  long id;
  String streamPath;

  @BeforeEach
  void setUp() {
    anonymous = new ApiClient(port);
    owner = new ApiClient(port);
    ownerName = "u" + UUID.randomUUID().toString().substring(0, 8);
    owner.signupAndLogin(ownerName, "password1234");
    JsonNode b = owner.post("/api/broadcasts", Map.of("title", "검색용 " + ownerName + " 방송")).json();
    id = b.get("id").asLong();
    streamPath = b.get("streamPath").asText();
  }

  @Test
  void 로그인한_사용자는_채팅을_보내고_누구나_읽을_수_있다() {
    assertThat(owner.post(chats(), Map.of("content", "안녕")).status()).isEqualTo(201);
    long secondId = owner.post(chats(), Map.of("content", "두번째")).json().get("id").asLong();

    JsonNode all = anonymous.get(chats()).json();
    assertThat(all).hasSize(2);
    assertThat(all.get(0).get("content").asText()).isEqualTo("안녕");
    assertThat(all.get(0).get("username").asText()).isEqualTo(ownerName);

    // after 이후 메시지만
    assertThat(anonymous.get(chats() + "?after=" + secondId).json()).isEmpty();
  }

  @Test
  void 비로그인_사용자는_채팅을_보낼_수_없다() {
    assertThat(anonymous.post(chats(), Map.of("content", "x")).status()).isEqualTo(401);
  }

  @Test
  void 빈_채팅과_종료된_방송_채팅은_거절한다() {
    assertThat(owner.post(chats(), Map.of("content", " ")).status()).isEqualTo(400);
    owner.post("/api/broadcasts/" + id + "/end", null);
    assertThat(owner.post(chats(), Map.of("content", "x")).status()).isEqualTo(409);
  }

  @Test
  void 제목이나_방송자_이름으로_검색한다() {
    assertThat(anonymous.get("/api/broadcasts?q=" + ownerName).json()).hasSize(1);
    assertThat(anonymous.get("/api/broadcasts?q=" + ownerName.toUpperCase()).json()).hasSize(1);
    assertThat(anonymous.get("/api/broadcasts?q=없는검색어zz").json()).isEmpty();
  }

  @Test
  void 방송_중이면_시청자_수를_보여준다() {
    var b = broadcasts.findById(id).orElseThrow();
    b.markLive();
    broadcasts.save(b);
    ((FakeMediaServerClient) mediaServer).viewers.put(streamPath, 3);

    assertThat(anonymous.get("/api/broadcasts/" + id).json().get("viewerCount").asInt())
        .isEqualTo(3);
  }

  private String chats() {
    return "/api/broadcasts/" + id + "/chats";
  }
}
