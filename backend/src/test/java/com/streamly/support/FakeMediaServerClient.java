package com.streamly.support;

import com.streamly.media.MediaServerClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 테스트에서 MediaMTX 대신 쓰는 가짜. 끊은 경로를 기록만 한다.
public class FakeMediaServerClient implements MediaServerClient {

  public final List<String> kicked = new ArrayList<>();

  @Override
  public void kick(String streamPath) {
    kicked.add(streamPath);
  }

  @Override
  public boolean isPublishing(String streamPath) {
    return false;
  }

  public final Map<String, Integer> viewers = new HashMap<>();

  @Override
  public Map<String, Integer> viewerCounts() {
    return viewers;
  }
}
