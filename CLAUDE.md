# CLAUDE.md — 에이전트 작업 규칙

Streamly: 브라우저로 송출하고 시청하는 실시간 라이브 방송 웹 서비스 (공모 제출작, 1인 개발).
마감 2026-11-19(목) 23:59, 공개 GitHub 저장소 URL로 제출한다.
목표는 "사람의 판단을 검증 가능한 신호(인수 테스트, CI)로 바꾸고, 에이전트가 그 신호를 기준으로 스스로 고치는 것".

## 절대 규칙 (CI의 guard 잡이 검사함)

- 다음 경로는 수정하지 않는다. 정답 기준과 자동화 규칙이다.
  - `backend/src/test/java/**/acceptance/` (승인된 인수 테스트)
  - `.github/`
  - `CLAUDE.md`
- 테스트를 삭제하거나, `@Disabled` 를 붙이거나, 단언을 약하게 바꾸거나, 빌드에서 제외하지 않는다.
- 인수 테스트가 틀렸다고 생각되면 코드를 억지로 맞추지 말고, 이유를 남기고 멈춘다.
- 비밀값(토큰, 비밀번호)을 코드나 설정 파일에 넣지 않는다. 환경변수로 받고 `.env.example` 에 예시만 둔다.
- 비밀번호, 세션 ID, 송출 토큰, `Authorization` 헤더는 로그에 남기지 않는다.

## 사람이 판단하는 지점 (이 외에는 에이전트가 진행)

1. 이슈에 목표와 인수 조건을 쓴다.
2. 인수 테스트를 검토하고 직접 커밋한다.
3. `needs-human` 라벨이 붙은 PR/이슈를 처리한다.

## 멈추고 `needs-human` 을 붙이는 경우

1. 인수 테스트가 틀렸다고 판단될 때
2. 새 의존성(Gradle, npm)을 추가해야 할 때
3. 인증·인가 방식이나 보안 설정(Caddy 라우팅, CORS, CSRF, `/internal/*`)을 바꿔야 할 때
4. 이미 머지된 DB 마이그레이션을 고쳐야 할 때
5. 같은 CI 실패를 자동 수정으로 3번 고쳐도 통과하지 못할 때

멈출 때는 무엇을 시도했고 왜 막혔는지 이슈나 PR에 남긴다.

## 범위

- 필수 기능, README, 시연 영상이 끝나기 전에는 선택 기능(댓글, 녹화·다시보기, 도네이션)을 시작하지 않는다.
- 앱은 1대만 운영한다. 수평 확장, 트래픽 급증 대응, Kafka, Redis는 범위 밖이다.
- 트랜스코딩(화질 변환)은 하지 않는다.
- 지원 환경
  - PC 송출: Chrome 최신 (맥은 탭 소리만, 윈도우는 시스템 소리 가능)
  - 모바일 송출: Android Chrome, iOS Safari
  - 시청: 위 브라우저 전부
  - 그 밖의 환경(Firefox, PC Safari 송출 등)은 범위 밖이며 README에 적는다.

## 아키텍처

| 구성 | 역할 |
|---|---|
| Spring Boot 3.5 / Java 21 | API, 인증·인가, 방송 상태 관리, MediaMTX 인증 훅 |
| PostgreSQL | 회원, 방송 정보 영구 저장 (Flyway로 스키마 관리) |
| MediaMTX | 브라우저 WebRTC 송출(WHIP)과 시청(WHEP)을 중계. 직접 구현하지 않음 |
| Next.js (React + TypeScript) | 화면. 송출·시청은 브라우저에서 처리 |
| Caddy + sslip.io | HTTPS(화면 공유·카메라는 HTTPS 필수)와 라우팅 |
| OCI Ampere A1 (ARM) | 운영 서버. Docker 이미지는 arm64 지원 버전만 쓴다 |

Caddy 라우팅 (운영)

- `/api/*` → 백엔드
- `/<streamPath>/whip`, `/<streamPath>/whep` → MediaMTX
- `/internal/*` → 외부 차단 (MediaMTX ↔ 백엔드 내부 전용)
- 나머지 → 프론트엔드

프론트와 API는 같은 도메인이다. CORS는 열지 않는다. 로컬 개발에서는 Next.js rewrites로 `/api` 를 백엔드에 넘긴다.

## 인증·인가

- Spring Security 세션 쿠키 방식 (`HttpOnly`, `Secure`, `SameSite=Lax`). 세션은 메모리에 둔다. JWT로 바꾸려면 먼저 사람에게 묻는다.
- CSRF는 켠다. `CookieCsrfTokenRepository` 로 `XSRF-TOKEN` 쿠키를 내려주고, 프론트는 변경 요청마다 `X-XSRF-TOKEN` 헤더를 붙인다. `/internal/*` 만 CSRF에서 제외한다.
- 인가 규칙 (서버에서 검증한다. 정답은 `acceptance/` 인수 테스트)

| 기능 | 비로그인 | 방송 소유자 | 다른 사용자 |
|---|---|---|---|
| 방송 목록·상세 조회, 시청 | 허용 | 허용 | 허용 |
| 방송 등록 | 401 | 허용 | 허용(본인 방송) |
| 방송 수정·삭제 | 401 | 허용 | 403 |
| 송출 토큰 발급, 송출 종료 | 401 | 허용 | 403 |

- 심사용 테스트 계정 2개(방송 소유자용, 다른 사용자용)는 앱 시작 시 없으면 만든다. 비밀번호는 환경변수 `DEMO_PASSWORD` 로 받는다. `POST /api/auth/demo-login` 으로 원클릭 로그인한다.

## 방송 상태

- 방송 1건은 송출 1회용이다. 상태는 `READY → LIVE → ENDED` 로만 바뀐다. ENDED가 되면 다시 송출할 수 없다. 다시 방송하려면 새로 등록한다.
- 송출이 끊기면 30초 유예한다. 유예 중 같은 방송으로 다시 송출하면 LIVE를 이어가고, 30초가 지나면 ENDED로 바꾼다.
- 소유자가 종료 API를 호출하면 즉시 ENDED로 바꾸고 MediaMTX 송출 세션을 끊는다.
- 송출 토큰은 방송 1개에만 유효하고 10분 뒤 만료된다. 유예 중 재접속은 만료 전 토큰을 그대로 쓴다.
- MediaMTX 인증 훅은 송출 요청마다 토큰과 소유자를 검증한다. 상태가 READY 또는 LIVE(유예 중 포함)이면 허용하고, ENDED이면 거절한다. 시청(read)은 인증 없이 허용한다.
- 서버 재시작 후에는 MediaMTX의 실제 송출 상태와 DB 상태를 맞춘다.
- 상태가 바뀔 때마다 방송 ID, 이전·다음 상태, 원인을 INFO 로그로 남긴다.
- 시청 화면의 종료 판단은 서버의 방송 상태를 기준으로 한다. 유예 중에는 "다시 연결 중"을 보여준다.

## 백엔드 코드 규칙

- 패키지는 기능별로 나눈다: `com.streamly.<기능>` (예: `auth`, `broadcast`, `media`, `common`)
- 기능 안에서는 `Controller → Service → Repository` 만 쓴다. 별도 도메인 계층, 포트·어댑터, 매퍼 클래스는 만들지 않는다.
  예외: MediaMTX Control API 호출은 `MediaServerClient` 인터페이스 뒤에 둔다 (테스트에서 가짜 구현 사용).
- 요청/응답 DTO는 `record` 로 만든다.
- 에러 응답은 모두 `ProblemDetail` (RFC 9457) 로 통일한다. 내부 예외 메시지나 스택트레이스는 응답에 넣지 않는다.
- DB 스키마는 Flyway 마이그레이션으로만 바꾼다 (`ddl-auto: validate`). 머지된 마이그레이션 파일은 고치지 않고 새 파일을 추가한다.
- 커밋 전에 `./gradlew spotlessApply` 를 실행한다.

## 테스트

- 새 API마다 `@IntegrationTest` (Testcontainers PostgreSQL + Flyway) 로 성공 케이스 1개와 거절 케이스(401/403/404)를 함께 쓴다.
- 단위 테스트는 상태 전환처럼 로직이 있는 곳에만 쓴다.
- 인가 규칙의 정답은 `acceptance/` 인수 테스트다. 에이전트의 테스트는 그 아래 세부 케이스를 다룬다.
- MediaMTX는 테스트에서 띄우지 않는다. 인증 훅과 이벤트 API는 HTTP로 직접 호출해서 테스트한다. 실제 송출은 실기기 점검에서 사람이 확인한다.

## 프론트엔드 (frontend/, Next.js, 에이전트 담당)

- App Router를 쓴다. 송출·시청 화면은 클라이언트 컴포넌트(`'use client'`)로 만든다.
- 데이터는 브라우저에서 `/api` 를 호출해 가져온다. 서버 컴포넌트에서 데이터를 가져오지 않는다.
- API 타입은 손으로 쓰지 않는다. 백엔드 OpenAPI 스펙에서 생성한 `src/api/schema.d.ts` 만 쓴다.
  (CI가 매번 다시 생성하므로, 백엔드와 안 맞으면 타입 검사에서 실패한다)
- 송출·시청 로직은 검증된 테스트 키트(`studio.html`, `watch.html`)의 WHIP/WHEP, 오디오 믹싱, 카메라 전환 로직을 옮긴다.
- 배포는 `output: 'standalone'` Node 컨테이너로 한다.
- `package.json` 에 `build`, `lint` 스크립트를 둔다. CI는 lint, 타입 검사, build까지만 확인한다. 화면 동작은 사람이 실기기로 점검한다.

## 작업 방식

- 이슈 1개에 PR 1개. 브랜치 이름은 Linear가 만든 이름을 쓴다.
- PR 제목에 `STR-<번호>` 를 넣는다. PR 설명에는 완료 조건 체크리스트와 확인 방법을 적는다.
- main에는 PR로만 들어간다. CI(`backend`, `frontend`, `guard`)가 통과해야 하고, 머지는 사람이 한다.
- 커밋 메시지: `feat: ...`, `fix: ...`, `test: ...`, `chore: ...`, 자동 수정은 `fix(autofix): ...`
- 주석, PR 설명, 커밋 본문, README는 한국어로 쓴다. 식별자와 커밋 접두사는 영어로 쓴다.
- 오픈소스나 기존 코드를 가져다 쓰면 README에 출처를 적는다.

## README 필수 항목

- 서비스 주소 (실제 접속 URL)
- 테스트 계정 정보와 원클릭 로그인 사용법
- 실행 방법: 실행 환경, 환경변수 예시
- 기능 확인 절차: PC·모바일 송출, 시청, 종료 방법과 지원 환경
- 시연 영상 링크 (PC·모바일 송출 → 다른 기기 시청 → 종료)
- 오픈소스 출처, AI 개발 도구 활용 방식

## 명령어

```bash
docker compose -f infra/docker-compose.yml up -d   # 로컬 인프라 (PostgreSQL, MediaMTX)
cd backend && ./gradlew bootRun                    # 서버 실행
cd backend && ./gradlew spotlessApply test         # 포맷 + 테스트
cd frontend && npm run lint && npm run build       # 프론트 검사
```
