package com.streamly.donation;

import com.streamly.auth.LoginUser;
import com.streamly.common.StreamlyProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DonationController {

  private final DonationService service;
  private final DonationRepository donations;
  private final StreamlyProperties props;

  public DonationController(
      DonationService service, DonationRepository donations, StreamlyProperties props) {
    this.service = service;
    this.donations = donations;
    this.props = props;
  }

  public record OrderRequest(@NotNull Long amount, @Size(max = 100) String message) {}

  public record OrderResponse(
      String orderId, long amount, String orderName, String clientKey, String customerKey) {}

  public record ConfirmRequest(
      @NotBlank String paymentKey, @NotBlank String orderId, @NotNull Long amount) {}

  public record DonationResponse(
      Long id, String donorName, long amount, String message, Instant paidAt) {
    static DonationResponse from(Donation d) {
      return new DonationResponse(
          d.getId(), d.getDonor().getUsername(), d.getAmount(), d.getMessage(), d.getPaidAt());
    }
  }

  public record DonationSummary(long total, List<DonationResponse> donations) {}

  @PostMapping("/api/broadcasts/{broadcastId}/donations")
  @ResponseStatus(HttpStatus.CREATED)
  public OrderResponse order(
      @AuthenticationPrincipal LoginUser user,
      @PathVariable Long broadcastId,
      @Valid @RequestBody OrderRequest req) {
    Donation d = service.order(user.id(), broadcastId, req.amount(), req.message());
    return new OrderResponse(
        d.getOrderId(),
        d.getAmount(),
        "Streamly 후원 " + d.getAmount() + "원",
        props.tossClientKey(),
        "user_" + user.id());
  }

  @PostMapping("/api/donations/confirm")
  @Transactional
  public DonationResponse confirm(
      @AuthenticationPrincipal LoginUser user, @Valid @RequestBody ConfirmRequest req) {
    return DonationResponse.from(
        service.confirm(user.id(), req.paymentKey(), req.orderId(), req.amount()));
  }

  @GetMapping("/api/broadcasts/{broadcastId}/donations")
  @Transactional(readOnly = true)
  public DonationSummary list(@PathVariable Long broadcastId) {
    return new DonationSummary(
        donations.sumPaid(broadcastId),
        donations.findPaid(broadcastId).stream().map(DonationResponse::from).toList());
  }
}
