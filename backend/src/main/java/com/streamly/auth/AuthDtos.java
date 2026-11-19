package com.streamly.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
  private AuthDtos() {}

  public record SignupRequest(
      @NotBlank @Size(min = 3, max = 30) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String username,
      @NotBlank @Size(min = 8, max = 72) String password) {}

  public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

  public record DemoLoginRequest(@NotBlank @Pattern(regexp = "owner|viewer") String account) {}

  public record MeResponse(Long id, String username) {}
}
