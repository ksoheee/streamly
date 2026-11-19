package com.streamly.recording;

import com.streamly.auth.LoginUser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class RecordingController {

  private final RecordingRepository recordings;
  private final RecordingService service;

  public RecordingController(RecordingRepository recordings, RecordingService service) {
    this.recordings = recordings;
    this.service = service;
  }

  public record RecordingResponse(
      Long id,
      Long broadcastId,
      String title,
      Long ownerId,
      String ownerName,
      double durationSeconds,
      long sizeBytes,
      Instant createdAt) {
    static RecordingResponse from(Recording r) {
      var b = r.getBroadcast();
      return new RecordingResponse(
          r.getId(),
          b.getId(),
          b.getTitle(),
          b.getOwner().getId(),
          b.getOwner().getUsername(),
          r.getDurationSeconds(),
          r.getSizeBytes(),
          r.getCreatedAt());
    }
  }

  @GetMapping("/api/recordings")
  @Transactional(readOnly = true)
  public List<RecordingResponse> list() {
    return recordings.findAllNewest().stream().map(RecordingResponse::from).toList();
  }

  @GetMapping("/api/broadcasts/{broadcastId}/recordings")
  @Transactional(readOnly = true)
  public List<RecordingResponse> ofBroadcast(@PathVariable Long broadcastId) {
    return recordings.findByBroadcast(broadcastId).stream().map(RecordingResponse::from).toList();
  }

  @GetMapping("/api/recordings/{id}")
  @Transactional(readOnly = true)
  public RecordingResponse get(@PathVariable Long id) {
    return RecordingResponse.from(find(id));
  }

  // Resource 로 돌려주면 Spring 이 Range 요청(구간 재생·탐색)을 처리한다
  @GetMapping("/api/recordings/{id}/video")
  @Transactional(readOnly = true)
  public ResponseEntity<Resource> video(@PathVariable Long id) {
    Path file =
        service
            .resolve(find(id).getFilePath())
            .filter(Files::isRegularFile)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "녹화 파일이 없습니다."));
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("video/mp4"))
        .body(new FileSystemResource(file));
  }

  @DeleteMapping("/api/recordings/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void delete(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
    Recording r = find(id);
    if (!r.getBroadcast().isOwnedBy(user.id())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "방송 소유자만 삭제할 수 있습니다.");
    }
    service.delete(r);
  }

  // MediaMTX runOnRecordSegmentComplete 가 wget 으로 부른다. 외부 접근은 Caddy 에서 막는다.
  @PostMapping("/internal/mediamtx/recordings")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void segmentComplete(
      @RequestParam String path,
      @RequestParam String file,
      @RequestParam(defaultValue = "0") String duration) {
    service.onSegmentComplete(path, file, parseSeconds(duration));
  }

  // MediaMTX 는 "1m2.5s" 같은 Go duration 이나 초 숫자를 넘긴다
  static double parseSeconds(String d) {
    try {
      return Double.parseDouble(d);
    } catch (NumberFormatException ignored) {
      double total = 0;
      var m = java.util.regex.Pattern.compile("([0-9.]+)(h|ms|m|s)").matcher(d);
      while (m.find()) {
        double v = Double.parseDouble(m.group(1));
        total +=
            switch (m.group(2)) {
              case "h" -> v * 3600;
              case "m" -> v * 60;
              case "ms" -> v / 1000;
              default -> v;
            };
      }
      return total;
    }
  }

  private Recording find(Long id) {
    return recordings
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "녹화가 없습니다."));
  }
}
