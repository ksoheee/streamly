package com.streamly.recording;

import com.streamly.broadcast.BroadcastRepository;
import com.streamly.common.StreamlyProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RecordingService {

  private static final Logger log = LoggerFactory.getLogger(RecordingService.class);
  private static final String MEDIAMTX_ROOT = "/recordings/";

  private final RecordingRepository recordings;
  private final BroadcastRepository broadcasts;
  private final StreamlyProperties props;

  public RecordingService(
      RecordingRepository recordings, BroadcastRepository broadcasts, StreamlyProperties props) {
    this.recordings = recordings;
    this.broadcasts = broadcasts;
    this.props = props;
  }

  /** MediaMTX runOnRecordSegmentComplete: 녹화 파일 하나가 완성됐다. */
  public void onSegmentComplete(String streamPath, String mediamtxFile, double durationSeconds) {
    String relative =
        mediamtxFile.startsWith(MEDIAMTX_ROOT)
            ? mediamtxFile.substring(MEDIAMTX_ROOT.length())
            : mediamtxFile;
    Optional<Path> file = resolve(relative);
    if (file.isEmpty()
        || !Files.isRegularFile(file.get())
        || recordings.existsByFilePath(relative)) {
      log.warn("녹화 파일을 등록하지 않음: {}", mediamtxFile);
      return;
    }
    broadcasts
        .findByStreamPath(streamPath)
        .ifPresent(
            b -> {
              recordings.save(new Recording(b, relative, durationSeconds, size(file.get())));
              log.info("방송 {} 녹화 저장: {} ({}초)", b.getId(), relative, (long) durationSeconds);
            });
  }

  /** 녹화 폴더 밖을 가리키는 경로(../ 등)는 거부한다. */
  public Optional<Path> resolve(String relative) {
    Path root = Path.of(props.recordingsDir()).toAbsolutePath().normalize();
    Path p = root.resolve(relative).normalize();
    return p.startsWith(root) ? Optional.of(p) : Optional.empty();
  }

  public void delete(Recording r) {
    resolve(r.getFilePath()).ifPresent(RecordingService::deleteQuietly);
    recordings.delete(r);
  }

  // 보관 기간이 지난 녹화를 지운다. MediaMTX recordDeleteAfter 도 파일을 지우지만 DB 행은 여기서 정리한다.
  @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1M")
  public void deleteExpired() {
    recordings
        .findByCreatedAtBefore(Instant.now().minus(props.recordingRetention()))
        .forEach(this::delete);
  }

  private static long size(Path p) {
    try {
      return Files.size(p);
    } catch (IOException e) {
      return 0;
    }
  }

  private static void deleteQuietly(Path p) {
    try {
      Files.deleteIfExists(p);
    } catch (IOException e) {
      log.warn("녹화 파일 삭제 실패: {}", p);
    }
  }
}
