package com.streamly.comment;

import com.streamly.auth.LoginUser;
import com.streamly.auth.UserRepository;
import com.streamly.broadcast.BroadcastRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class CommentController {

  private static final int PAGE = 20;

  private final CommentRepository comments;
  private final BroadcastRepository broadcasts;
  private final UserRepository users;

  public CommentController(
      CommentRepository comments, BroadcastRepository broadcasts, UserRepository users) {
    this.comments = comments;
    this.broadcasts = broadcasts;
    this.users = users;
  }

  public record CommentRequest(@NotBlank @Size(max = 500) String content) {}

  public record CommentResponse(
      Long id, Long userId, String username, String content, Instant createdAt, boolean edited) {
    static CommentResponse from(Comment c) {
      return new CommentResponse(
          c.getId(),
          c.getUser().getId(),
          c.getUser().getUsername(),
          c.getContent(),
          c.getCreatedAt(),
          c.getUpdatedAt() != null);
    }
  }

  @GetMapping("/api/broadcasts/{broadcastId}/comments")
  @Transactional(readOnly = true)
  public List<CommentResponse> list(
      @PathVariable Long broadcastId,
      @RequestParam(defaultValue = "" + Long.MAX_VALUE) long before) {
    requireBroadcast(broadcastId);
    return comments.findPage(broadcastId, before, Limit.of(PAGE)).stream()
        .map(CommentResponse::from)
        .toList();
  }

  @PostMapping("/api/broadcasts/{broadcastId}/comments")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public CommentResponse create(
      @AuthenticationPrincipal LoginUser user,
      @PathVariable Long broadcastId,
      @Valid @RequestBody CommentRequest req) {
    requireBroadcast(broadcastId);
    return CommentResponse.from(
        comments.save(
            new Comment(broadcastId, users.getReferenceById(user.id()), req.content().strip())));
  }

  @PutMapping("/api/comments/{id}")
  @Transactional
  public CommentResponse update(
      @AuthenticationPrincipal LoginUser user,
      @PathVariable Long id,
      @Valid @RequestBody CommentRequest req) {
    Comment c = findOwn(user, id);
    c.edit(req.content().strip());
    return CommentResponse.from(c);
  }

  @DeleteMapping("/api/comments/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void delete(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
    comments.delete(findOwn(user, id));
  }

  private Comment findOwn(LoginUser user, Long id) {
    Comment c =
        comments
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글이 없습니다."));
    if (!c.isWrittenBy(user.id())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "작성자만 할 수 있습니다.");
    }
    return c;
  }

  private void requireBroadcast(Long id) {
    if (!broadcasts.existsById(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "방송이 없습니다.");
    }
  }
}
