package com.streamly.auth;

import com.streamly.auth.AuthDtos.DemoLoginRequest;
import com.streamly.auth.AuthDtos.LoginRequest;
import com.streamly.auth.AuthDtos.MeResponse;
import com.streamly.auth.AuthDtos.SignupRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final AuthenticationManager authManager;
  private final LoginUserDetailsService userDetails;
  private final SecurityContextRepository contextRepository;

  public AuthController(
      UserRepository users,
      PasswordEncoder encoder,
      AuthenticationManager authManager,
      LoginUserDetailsService userDetails,
      SecurityContextRepository contextRepository) {
    this.users = users;
    this.encoder = encoder;
    this.authManager = authManager;
    this.userDetails = userDetails;
    this.contextRepository = contextRepository;
  }

  // 프론트가 처음에 한 번 불러 XSRF-TOKEN 쿠키를 받는다
  @GetMapping("/csrf")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void csrf(CsrfToken token) {
    token.getToken();
  }

  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public MeResponse signup(@Valid @RequestBody SignupRequest req) {
    if (users.existsByUsername(req.username())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다.");
    }
    User user = users.save(new User(req.username(), encoder.encode(req.password())));
    return new MeResponse(user.getId(), user.getUsername());
  }

  @PostMapping("/login")
  public MeResponse login(
      @Valid @RequestBody LoginRequest req,
      HttpServletRequest request,
      HttpServletResponse response) {
    Authentication auth;
    try {
      auth =
          authManager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(req.username(), req.password()));
    } catch (BadCredentialsException e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 틀렸습니다.");
    }
    return startSession(auth, request, response);
  }

  @PostMapping("/demo-login")
  public MeResponse demoLogin(
      @Valid @RequestBody DemoLoginRequest req,
      HttpServletRequest request,
      HttpServletResponse response) {
    LoginUser user =
        (LoginUser) userDetails.loadUserByUsername(DemoAccounts.usernameOf(req.account()));
    Authentication auth =
        UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
    return startSession(auth, request, response);
  }

  @GetMapping("/me")
  public MeResponse me(@AuthenticationPrincipal LoginUser user) {
    return new MeResponse(user.id(), user.username());
  }

  private MeResponse startSession(
      Authentication auth, HttpServletRequest request, HttpServletResponse response) {
    if (request.getSession(false) != null) {
      request.changeSessionId(); // 세션 고정 공격 방지
    }
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(auth);
    SecurityContextHolder.setContext(context);
    contextRepository.saveContext(context, request, response);
    LoginUser user = (LoginUser) auth.getPrincipal();
    return new MeResponse(user.id(), user.username());
  }
}
