package com.streamly.donation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamly.support.ApiClient;
import com.streamly.support.FakePaymentGateway;
import com.streamly.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

// [선택 기능] 도네이션: 주문 → 승인 검증 → 중복 적립 방지 → 방송별 내역·합계 (STR-24)
@IntegrationTest
class DonationTest {

  @LocalServerPort int port;
  @Autowired PaymentGateway gateway;

  ApiClient owner;
  ApiClient donor;
  ApiClient anonymous;
  long broadcastId;

  @BeforeEach
  void setUp() {
    owner = new ApiClient(port);
    donor = new ApiClient(port);
    anonymous = new ApiClient(port);
    owner.signupAndLogin(name(), "password1234");
    donor.signupAndLogin(name(), "password1234");
    broadcastId = owner.post("/api/broadcasts", Map.of("title", "후원 방송")).json().get("id").asLong();
    fake().overrideAmount = null;
  }

  @Test
  void 주문하고_승인하면_방송별_내역과_합계에_반영된다() {
    String orderId = order(5000, "응원해요").get("orderId").asText();
    ApiClient.Response ok = confirm(donor, "pay_" + orderId, orderId, 5000);
    assertThat(ok.status()).isEqualTo(200);

    JsonNode summary = anonymous.get(donations()).json();
    assertThat(summary.get("total").asLong()).isEqualTo(5000);
    assertThat(summary.get("donations").get(0).get("message").asText()).isEqualTo("응원해요");
  }

  @Test
  void 같은_승인이_여러_번_와도_한_번만_적립된다() {
    String orderId = order(3000, null).get("orderId").asText();
    int before = fake().calls.get();
    confirm(donor, "pay_dup", orderId, 3000);
    assertThat(confirm(donor, "pay_dup", orderId, 3000).status()).isEqualTo(200);
    assertThat(confirm(donor, "pay_dup", orderId, 3000).status()).isEqualTo(200);

    assertThat(fake().calls.get() - before).isEqualTo(1); // 결제사 승인도 한 번만
    assertThat(anonymous.get(donations()).json().get("total").asLong()).isEqualTo(3000);
  }

  @Test
  void 주문과_다른_금액은_승인하지_않는다() {
    String orderId = order(10000, null).get("orderId").asText();
    assertThat(confirm(donor, "pay_x", orderId, 100).status()).isEqualTo(400);

    fake().overrideAmount = 100L; // 결제사 결과가 주문과 다르면
    assertThat(confirm(donor, "pay_y", orderId, 10000).status()).isEqualTo(502);
    assertThat(anonymous.get(donations()).json().get("total").asLong()).isZero();
  }

  @Test
  void 남의_주문은_승인할_수_없고_비로그인은_후원할_수_없다() {
    String orderId = order(1000, null).get("orderId").asText();
    assertThat(confirm(owner, "pay_z", orderId, 1000).status()).isEqualTo(403);
    assertThat(anonymous.post(donations(), Map.of("amount", 1000)).status()).isEqualTo(401);
  }

  @Test
  void 금액_범위와_자기_방송_후원을_거절한다() {
    assertThat(donor.post(donations(), Map.of("amount", 500)).status()).isEqualTo(400);
    assertThat(donor.post(donations(), Map.of("amount", 100_100)).status()).isEqualTo(400);
    assertThat(donor.post(donations(), Map.of("amount", 1050)).status()).isEqualTo(400);
    assertThat(owner.post(donations(), Map.of("amount", 1000)).status()).isEqualTo(400);
  }

  private JsonNode order(long amount, String message) {
    ApiClient.Response r =
        donor.post(
            donations(),
            message == null
                ? Map.of("amount", amount)
                : Map.of("amount", amount, "message", message));
    assertThat(r.status()).isEqualTo(201);
    return r.json();
  }

  private ApiClient.Response confirm(
      ApiClient who, String paymentKey, String orderId, long amount) {
    return who.post(
        "/api/donations/confirm",
        Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount));
  }

  private String donations() {
    return "/api/broadcasts/" + broadcastId + "/donations";
  }

  private FakePaymentGateway fake() {
    return (FakePaymentGateway) gateway;
  }

  private static String name() {
    return "u" + UUID.randomUUID().toString().substring(0, 8);
  }
}
