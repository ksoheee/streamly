package com.streamly.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

// 브라우저 한 명처럼 쿠키(세션, XSRF-TOKEN)를 유지하며 API를 부르는 테스트용 클라이언트.
// 변경 요청에는 브라우저 프론트처럼 X-XSRF-TOKEN 헤더를 붙인다.
public class ApiClient {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final String baseUrl;
  private final CookieManager cookies = new CookieManager();
  private final HttpClient http;

  public ApiClient(int port) {
    this.baseUrl = "http://localhost:" + port;
    this.http = HttpClient.newBuilder().cookieHandler(cookies).build();
  }

  public record Response(int status, String body) {
    public JsonNode json() {
      try {
        return JSON.readTree(body);
      } catch (Exception e) {
        throw new IllegalStateException("JSON이 아닌 응답: " + body, e);
      }
    }
  }

  public CookieManager cookies() {
    return cookies;
  }

  public HttpClient http() {
    return http;
  }

  public Response get(String path) {
    return send("GET", path, null);
  }

  public Response post(String path, Object body) {
    return send("POST", path, body);
  }

  public Response put(String path, Object body) {
    return send("PUT", path, body);
  }

  public Response delete(String path) {
    return send("DELETE", path, null);
  }

  public Response signupAndLogin(String username, String password) {
    post("/api/auth/signup", new Credentials(username, password));
    return post("/api/auth/login", new Credentials(username, password));
  }

  public record Credentials(String username, String password) {}

  private Response send(String method, String path, Object body) {
    try {
      boolean mutating = !method.equals("GET");
      if (mutating && csrfToken() == null) {
        send("GET", "/api/auth/csrf", null);
      }
      HttpRequest.Builder req =
          HttpRequest.newBuilder(URI.create(baseUrl + path))
              .header("Content-Type", "application/json")
              .header("Accept", "application/json");
      String token = csrfToken();
      if (mutating && token != null) {
        req.header("X-XSRF-TOKEN", token);
      }
      HttpRequest.BodyPublisher publisher =
          body == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body));
      HttpResponse<String> res =
          http.send(req.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
      return new Response(res.statusCode(), res.body());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String csrfToken() {
    return cookies.getCookieStore().getCookies().stream()
        .filter(c -> c.getName().equals("XSRF-TOKEN"))
        .map(HttpCookie::getValue)
        .findFirst()
        .orElse(null);
  }
}
