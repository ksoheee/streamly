package com.streamly.support;

import com.streamly.donation.PaymentGateway;
import com.streamly.media.MediaServerClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// 테스트용 PostgreSQL을 컨테이너로 띄운다. 운영과 같은 DB로 검증하기 위함.
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgresContainer() {
    return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
  }

  @Bean
  @Primary
  MediaServerClient fakeMediaServerClient() {
    return new FakeMediaServerClient();
  }

  @Bean
  @Primary
  PaymentGateway fakePaymentGateway() {
    return new FakePaymentGateway();
  }
}
