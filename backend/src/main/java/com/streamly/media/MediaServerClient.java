package com.streamly.media;

import java.util.Map;

// MediaMTX Control API. 테스트에서는 가짜 구현으로 바꾼다.
public interface MediaServerClient {

  /** 경로의 송출 연결을 끊는다. 송출 중이 아니면 아무것도 하지 않는다. */
  void kick(String streamPath);

  /** 경로에 지금 송출이 들어오고 있는지. */
  boolean isPublishing(String streamPath);

  /** 경로별 지금 시청 중인 연결 수. 실패하면 빈 맵. */
  Map<String, Integer> viewerCounts();
}
