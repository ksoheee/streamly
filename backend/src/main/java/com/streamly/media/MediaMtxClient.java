package com.streamly.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamly.common.StreamlyProperties;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class MediaMtxClient implements MediaServerClient {

  private static final Logger log = LoggerFactory.getLogger(MediaMtxClient.class);

  private final RestClient http;

  public MediaMtxClient(StreamlyProperties props) {
    this.http = RestClient.builder().baseUrl(props.mediamtxApiUrl()).build();
  }

  @Override
  public void kick(String streamPath) {
    try {
      JsonNode source = path(streamPath).path("source");
      String type = source.path("type").asText();
      String id = source.path("id").asText();
      String resource =
          switch (type) {
            case "webRTCSession" -> "webrtcsessions";
            case "rtmpConn" -> "rtmpconns";
            case "rtspSession" -> "rtspsessions";
            case "srtConn" -> "srtconns";
            default -> null;
          };
      if (resource == null || id.isEmpty()) {
        return;
      }
      http.post().uri("/v3/{r}/kick/{id}", resource, id).retrieve().toBodilessEntity();
    } catch (HttpClientErrorException.NotFound e) {
      // 송출 중이 아님
    } catch (RestClientException e) {
      log.warn("MediaMTX 송출 끊기 실패: {}", e.getMessage());
    }
  }

  @Override
  public boolean isPublishing(String streamPath) {
    try {
      return path(streamPath).path("ready").asBoolean(false);
    } catch (HttpClientErrorException.NotFound e) {
      return false;
    }
  }

  @Override
  public Map<String, Integer> viewerCounts() {
    try {
      JsonNode items =
          http.get().uri("/v3/paths/list").retrieve().body(JsonNode.class).path("items");
      Map<String, Integer> counts = new HashMap<>();
      items.forEach(p -> counts.put(p.path("name").asText(), p.path("readers").size()));
      return counts;
    } catch (RestClientException e) {
      log.warn("MediaMTX 시청자 수 조회 실패: {}", e.getMessage());
      return Map.of();
    }
  }

  private JsonNode path(String streamPath) {
    return http.get().uri("/v3/paths/get/{p}", streamPath).retrieve().body(JsonNode.class);
  }
}
