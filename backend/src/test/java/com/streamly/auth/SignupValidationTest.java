package com.streamly.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamly.support.ApiClient;
import com.streamly.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

// 가입 입력이 틀리면 어느 칸이 왜 틀렸는지 알려준다
@IntegrationTest
class SignupValidationTest {

  @LocalServerPort int port;

  @Test
  void 이메일_아이디는_이유와_함께_거절한다() {
    ApiClient.Response r =
        new ApiClient(port)
            .post(
                "/api/auth/signup", Map.of("username", "sohee@test.com", "password", "qwer1234!"));
    assertThat(r.status()).isEqualTo(400);
    assertThat(r.json().get("detail").asText()).contains("영문, 숫자");
  }

  @Test
  void 짧은_비밀번호는_이유와_함께_거절한다() {
    ApiClient.Response r =
        new ApiClient(port)
            .post("/api/auth/signup", Map.of("username", "sohee", "password", "123"));
    assertThat(r.status()).isEqualTo(400);
    assertThat(r.json().get("detail").asText()).contains("비밀번호");
  }
}
