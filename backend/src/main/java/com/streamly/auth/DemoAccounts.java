package com.streamly.auth;

import com.streamly.common.StreamlyProperties;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// 심사용 테스트 계정 2개를 앱 시작 시 없으면 만든다. 비밀번호는 DEMO_PASSWORD 환경변수로 받는다.
@Component
public class DemoAccounts implements ApplicationRunner {

  public static final String OWNER = "demo-owner";
  public static final String VIEWER = "demo-viewer";
  private static final Logger log = LoggerFactory.getLogger(DemoAccounts.class);

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final StreamlyProperties props;

  public DemoAccounts(UserRepository users, PasswordEncoder encoder, StreamlyProperties props) {
    this.users = users;
    this.encoder = encoder;
    this.props = props;
  }

  @Override
  public void run(ApplicationArguments args) {
    String password = props.demoPassword();
    if (password == null || password.isBlank()) {
      // 비밀번호 로그인은 막고 원클릭 로그인만 되게 한다
      password = UUID.randomUUID().toString();
      log.info("DEMO_PASSWORD가 없어 테스트 계정은 원클릭 로그인으로만 쓸 수 있습니다.");
    }
    for (String name : new String[] {OWNER, VIEWER}) {
      if (!users.existsByUsername(name)) {
        users.save(new User(name, encoder.encode(password)));
      }
    }
  }

  public static String usernameOf(String account) {
    return account.equals("owner") ? OWNER : VIEWER;
  }
}
