package com.streamly.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class LoginUserDetailsService implements UserDetailsService {

  private final UserRepository users;

  public LoginUserDetailsService(UserRepository users) {
    this.users = users;
  }

  @Override
  public UserDetails loadUserByUsername(String username) {
    User user =
        users.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException(username));
    return new LoginUser(user.getId(), user.getUsername(), user.getPasswordHash());
  }
}
