package com.streamly.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
  private AuthDtos() {}

  public record SignupRequest(
      @NotBlank(message = "아이디를 입력해 주세요.")
          @Size(min = 3, max = 30, message = "아이디는 3~30자예요.")
          @Pattern(
              regexp = "^[A-Za-z0-9_-]+$",
              message = "아이디는 영문, 숫자, _, - 만 쓸 수 있어요. (이메일·한글·공백 불가)")
          String username,
      @NotBlank(message = "비밀번호를 입력해 주세요.") @Size(min = 8, max = 72, message = "비밀번호는 8~72자예요.")
          String password) {}

  public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

  public record DemoLoginRequest(@NotBlank @Pattern(regexp = "owner|viewer") String account) {}

  public record MeResponse(Long id, String username) {}
}
