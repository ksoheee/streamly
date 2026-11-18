package com.streamly.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamly.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

// [승인된 인수 테스트] 이 폴더(acceptance/)의 테스트는 정답 기준이다.
// 에이전트는 이 폴더를 수정할 수 없다. 변경하려면 사람이 직접 PR을 올리고 CODEOWNERS 승인을 받는다.
@IntegrationTest
class HealthAcceptanceTest {

  @Autowired TestRestTemplate rest;

  @Test
  void 서버가_뜨면_헬스체크가_UP을_반환한다() {
    ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"status\":\"UP\"");
  }
}
