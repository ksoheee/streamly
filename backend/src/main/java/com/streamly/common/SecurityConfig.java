package com.streamly.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper json) throws Exception {
    return http.csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    // SPA가 쿠키 값을 그대로 헤더에 넣는 방식 (XOR 마스킹 없이)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    // MediaMTX → 서버 내부 호출. 외부 노출은 Caddy에서 막는다.
                    .ignoringRequestMatchers("/internal/**"))
        .securityContext(c -> c.securityContextRepository(securityContextRepository()))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        HttpMethod.GET,
                        "/api/broadcasts",
                        "/api/broadcasts/*",
                        "/api/broadcasts/*/chats",
                        "/api/broadcasts/*/comments",
                        "/api/broadcasts/*/thumbnail",
                        "/api/broadcasts/*/recordings",
                        "/api/recordings",
                        "/api/recordings/*",
                        "/api/recordings/*/video")
                    .permitAll()
                    .requestMatchers(
                        "/api/auth/csrf",
                        "/api/auth/signup",
                        "/api/auth/login",
                        "/api/auth/demo-login")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) ->
                            problem(res, json, HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."))
                    .accessDeniedHandler(
                        (req, res, ex) -> problem(res, json, HttpStatus.FORBIDDEN, "권한이 없습니다.")))
        .logout(
            l ->
                l.logoutUrl("/api/auth/logout")
                    .logoutSuccessHandler(
                        (req, res, auth) -> res.setStatus(HttpServletResponse.SC_NO_CONTENT)))
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .build();
  }

  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
  }

  private static void problem(
      HttpServletResponse res, ObjectMapper json, HttpStatus status, String detail)
      throws IOException {
    res.setStatus(status.value());
    res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    res.setCharacterEncoding("UTF-8");
    json.writeValue(res.getOutputStream(), ProblemDetail.forStatusAndDetail(status, detail));
  }
}
