package com.chzzkclone;

import static org.assertj.core.api.Assertions.assertThat;

import com.chzzkclone.support.IntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

// OpenAPI 스펙을 build/openapi.json 으로 내보낸다.
// CI가 이 파일로 프론트엔드 TypeScript 타입을 생성하고 타입 검사를 돌린다. 백엔드 API가 바뀌면 프론트 빌드가 깨져서
// 불일치가 자동으로 감지된다.
@IntegrationTest
class OpenApiExportTest {

  @Autowired TestRestTemplate rest;

  @Test
  void OpenAPI_스펙을_파일로_내보낸다() throws Exception {
    String spec = rest.getForObject("/v3/api-docs", String.class);
    assertThat(spec).contains("\"openapi\"");

    Path out = Path.of("build", "openapi.json");
    Files.createDirectories(out.getParent());
    Files.writeString(out, spec);
  }
}
