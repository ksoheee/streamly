package com.streamly.broadcast;

import com.streamly.auth.LoginUser;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// 송출 화면이 주기적으로 올리는 JPEG 썸네일. 올리기는 방송 소유자만, 보기는 누구나.
@RestController
public class ThumbnailController {

  static final int MAX_BYTES = 300 * 1024;

  private final BroadcastRepository broadcasts;
  private final BroadcastThumbnailRepository thumbnails;

  public ThumbnailController(
      BroadcastRepository broadcasts, BroadcastThumbnailRepository thumbnails) {
    this.broadcasts = broadcasts;
    this.thumbnails = thumbnails;
  }

  @PutMapping(value = "/api/broadcasts/{id}/thumbnail", consumes = MediaType.IMAGE_JPEG_VALUE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void upload(
      @AuthenticationPrincipal LoginUser user, @PathVariable Long id, @RequestBody byte[] image) {
    Broadcast b =
        broadcasts
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "방송이 없습니다."));
    if (!b.isOwnedBy(user.id())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "방송 소유자만 할 수 있습니다.");
    }
    if (image.length == 0 || image.length > MAX_BYTES || !isJpeg(image)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "300KB 이하 JPEG만 올릴 수 있습니다.");
    }
    thumbnails
        .findById(id)
        .ifPresentOrElse(
            t -> t.replace(image), () -> thumbnails.save(new BroadcastThumbnail(id, image)));
    b.markThumbnailUpdated();
  }

  @GetMapping("/api/broadcasts/{id}/thumbnail")
  @Transactional(readOnly = true)
  public ResponseEntity<byte[]> get(@PathVariable Long id) {
    return thumbnails
        .findById(id)
        .map(
            t ->
                ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    // 주소에 ?v=갱신시각 을 붙여 부르므로 길게 캐시해도 된다
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                    .body(t.getImage()))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "썸네일이 없습니다."));
  }

  private static boolean isJpeg(byte[] b) {
    return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
  }
}
