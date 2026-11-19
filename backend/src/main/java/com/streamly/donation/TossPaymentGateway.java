package com.streamly.donation;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamly.common.StreamlyProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// 토스페이먼츠 결제 승인: POST /v1/payments/confirm (시크릿 키 Basic 인증)
@Component
public class TossPaymentGateway implements PaymentGateway {

  private final RestClient http;
  private final StreamlyProperties props;

  public TossPaymentGateway(StreamlyProperties props) {
    this.props = props;
    this.http = RestClient.builder().baseUrl("https://api.tosspayments.com").build();
  }

  @Override
  public Approval confirm(String paymentKey, String orderId, long amount) {
    String secret = props.tossSecretKey();
    if (secret == null || secret.isBlank()) {
      throw new PaymentException("결제 키가 설정되지 않았습니다.");
    }
    String auth =
        Base64.getEncoder().encodeToString((secret + ":").getBytes(StandardCharsets.UTF_8));
    try {
      JsonNode res =
          http.post()
              .uri("/v1/payments/confirm")
              .header("Authorization", "Basic " + auth)
              // 같은 결제를 다시 승인 요청해도 토스가 한 번만 처리하게 한다
              .header("Idempotency-Key", paymentKey)
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount))
              .retrieve()
              .body(JsonNode.class);
      return new Approval(
          res.path("paymentKey").asText(),
          res.path("orderId").asText(),
          res.path("totalAmount").asLong(),
          res.path("status").asText());
    } catch (RestClientResponseException e) {
      throw new PaymentException("결제 승인에 실패했습니다: " + e.getResponseBodyAsString());
    }
  }
}
