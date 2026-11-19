package com.streamly.auth;

import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

// 세션에 저장되는 로그인 사용자. 비밀번호 해시는 인증 과정에서만 쓴다.
public record LoginUser(Long id, String username, String password) implements UserDetails {

  @Override
  public List<GrantedAuthority> getAuthorities() {
    return List.of();
  }

  @Override
  public String getPassword() {
    return password;
  }

  @Override
  public String getUsername() {
    return username;
  }
}
