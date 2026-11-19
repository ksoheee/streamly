package com.streamly.broadcast;

import com.streamly.auth.LoginUser;
import com.streamly.broadcast.BroadcastDtos.BroadcastRequest;
import com.streamly.broadcast.BroadcastDtos.BroadcastResponse;
import com.streamly.broadcast.BroadcastDtos.PublishTokenResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/broadcasts")
public class BroadcastController {

  private final BroadcastService service;

  public BroadcastController(BroadcastService service) {
    this.service = service;
  }

  @GetMapping
  public List<BroadcastResponse> list(@RequestParam(required = false) BroadcastStatus status) {
    return service.list(status);
  }

  @GetMapping("/{id}")
  public BroadcastResponse get(@PathVariable Long id) {
    return service.get(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BroadcastResponse create(
      @AuthenticationPrincipal LoginUser user, @Valid @RequestBody BroadcastRequest req) {
    return service.create(user.id(), req);
  }

  @PutMapping("/{id}")
  public BroadcastResponse update(
      @AuthenticationPrincipal LoginUser user,
      @PathVariable Long id,
      @Valid @RequestBody BroadcastRequest req) {
    return service.update(user.id(), id, req);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
    service.delete(user.id(), id);
  }

  @PostMapping("/{id}/publish-token")
  public PublishTokenResponse publishToken(
      @AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
    return service.issuePublishToken(user.id(), id);
  }

  @PostMapping("/{id}/end")
  public BroadcastResponse end(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
    return service.end(user.id(), id);
  }
}
