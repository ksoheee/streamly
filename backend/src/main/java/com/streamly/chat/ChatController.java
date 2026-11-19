package com.streamly.chat;

import com.streamly.auth.LoginUser;
import com.streamly.auth.UserRepository;
import com.streamly.broadcast.BroadcastRepository;
import com.streamly.broadcast.BroadcastStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// 채팅. 시청 화면이 2초마다 after(마지막으로 받은 id) 이후 메시지를 가져간다.
// shortcut: 폴링 방식이라 시청자가 아주 많아지면 DB 조회가 늘어난다. 그때 WebSocket/SSE로 바꾼다.
@RestController
@RequestMapping("/api/broadcasts/{broadcastId}/chats")
public class ChatController {

  private static final int MAX = 50;

  private final ChatRepository chats;
  private final BroadcastRepository broadcasts;
  private final UserRepository users;

  public ChatController(
      ChatRepository chats, BroadcastRepository broadcasts, UserRepository users) {
    this.chats = chats;
    this.broadcasts = broadcasts;
    this.users = users;
  }

  public record ChatRequest(@NotBlank @Size(max = 200) String content) {}

  public record ChatResponse(Long id, String username, String content, Instant createdAt) {
    static ChatResponse from(ChatMessage m) {
      return new ChatResponse(
          m.getId(), m.getUser().getUsername(), m.getContent(), m.getCreatedAt());
    }
  }

  @GetMapping
  @Transactional(readOnly = true)
  public List<ChatResponse> list(
      @PathVariable Long broadcastId, @RequestParam(defaultValue = "0") long after) {
    requireBroadcast(broadcastId);
    // 최신 MAX개를 가져와 오래된 순으로 돌려준다
    return chats.findRecent(broadcastId, after, Limit.of(MAX)).reversed().stream()
        .map(ChatResponse::from)
        .toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public ChatResponse send(
      @AuthenticationPrincipal LoginUser user,
      @PathVariable Long broadcastId,
      @Valid @RequestBody ChatRequest req) {
    if (requireBroadcast(broadcastId) == BroadcastStatus.ENDED) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "종료된 방송에는 채팅할 수 없습니다.");
    }
    ChatMessage saved =
        chats.save(
            new ChatMessage(broadcastId, users.getReferenceById(user.id()), req.content().strip()));
    return ChatResponse.from(saved);
  }

  private BroadcastStatus requireBroadcast(Long id) {
    return broadcasts
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "방송이 없습니다."))
        .getStatus();
  }
}
