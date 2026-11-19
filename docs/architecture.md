# Streamly 설계 문서

서버를 어떻게 구성했는지, 각 부분을 어떻게 구현했는지, 어떤 선택지 중에서 왜 골랐는지를 정리한 문서입니다.
실행 방법과 기능 확인 절차는 [README](../README.md), 에이전트 작업 규칙은 [CLAUDE.md](../CLAUDE.md)를 보세요.

## 1. 전체 구성

```
                         ┌──────────────── 서버 1대 (docker compose) ────────────────┐
브라우저 ──HTTPS 443──▶  │ Caddy ─┬─ /api/*                 → app (Spring Boot :8080) ─── PostgreSQL
(PC·휴대폰)              │        ├─ /<경로>/whip, /whep    → MediaMTX (:8889)             │
                         │        ├─ /internal/*, /actuator/* → 404 (외부 차단)            │
                         │        └─ 나머지                 → frontend (Next.js :3000)     │
브라우저 ◀─WebRTC 미디어─▶│ MediaMTX :8189 (UDP, TCP)                                       │
                         │ MediaMTX ──송출 인증·상태 이벤트──▶ app /internal/mediamtx/*      │
                         │ app ──송출 끊기·상태 확인──▶ MediaMTX Control API (:9997, 내부)   │
                         └──────────────────────────────────────────────────────────────┘
```

| 구성 요소 | 역할 | 코드 |
|---|---|---|
| Caddy | HTTPS 인증서 자동 발급, 경로별 라우팅, 내부 경로 차단 | [`deploy/Caddyfile`](../deploy/Caddyfile) |
| MediaMTX | 브라우저 WebRTC 송출(WHIP)·시청(WHEP) 중계 | [`deploy/mediamtx.yml`](../deploy/mediamtx.yml) |
| app (Spring Boot) | 인증·인가, 방송 CRUD, 송출 토큰, 방송 상태 관리 | [`backend/`](../backend) |
| frontend (Next.js) | 화면, 송출(캡처 + WHIP)·시청(WHEP) | [`frontend/`](../frontend) |
| PostgreSQL | 회원, 방송 정보 영구 저장 | [`V1__init.sql`](../backend/src/main/resources/db/migration/V1__init.sql) |

외부에 열린 포트는 TCP 80·443(웹), TCP·UDP 8189(WebRTC 미디어)뿐입니다. DB(5432), 백엔드(8080), MediaMTX API(9997)는 compose 내부망에서만 접근합니다.

## 2. 핵심 흐름

### 송출 (PC 또는 휴대폰)

```
1. 소유자가 [방송 시작]       → POST /api/broadcasts/{id}/publish-token   (세션 + CSRF, 소유자만)
2. 서버                      → 송출 토큰 발급 (10분, 이 방송 경로 전용)
3. 브라우저                   → 화면/카메라 캡처 → POST /<streamPath>/whip  (Authorization: Bearer <토큰>)
4. MediaMTX                  → POST app/internal/mediamtx/auth            "이 토큰으로 이 경로에 송출해도 되나?"
5. 서버                      → 토큰·경로·만료·방송 상태(ENDED 아님) 확인 → 200 허용 / 401 거절
6. 송출이 실제로 시작되면     → MediaMTX runOnAvailable → POST app/internal/mediamtx/events?type=ready → 방송 LIVE
```

### 종료

- **소유자가 [방송 종료]** → `POST /api/broadcasts/{id}/end` → 방송 ENDED, 토큰 폐기, MediaMTX Control API로 송출 세션을 끊음(kick).
- **브라우저에서 '공유 중지'** → 프론트가 같은 종료 API를 호출.
- **네트워크가 끊김** → MediaMTX `runOnUnavailable` → 서버가 끊긴 시각만 기록(재연결 유예) → 30초 안에 다시 송출하지 않으면 스케줄러가 ENDED로 바꿈.

### 시청

시청 화면은 3초마다 `GET /api/broadcasts/{id}`로 **서버 상태**를 확인합니다. LIVE면 WHEP로 연결하고, 재연결 유예 중이면 "다시 연결 중…", ENDED면 "방송이 종료되었습니다"를 보여 줍니다. 시청(read)은 인증이 필요 없습니다.

## 3. 설계 결정

각 결정은 **선택지 → 선택 → 이유 → 대가(포기한 것)** 순서로 적었습니다.

### 3.1 송출 방식: 브라우저 WebRTC (WHIP/WHEP)

| 선택지 | 판단 |
|---|---|
| OBS 같은 송출 프로그램 → RTMP → HLS 변환 | 처음 뼈대(chzzk-clone)의 방식. 휴대폰 카메라 송출을 브라우저에서 할 수 없고, HLS는 지연이 수 초~수십 초 |
| **브라우저 → WebRTC (WHIP 송출 / WHEP 시청)** | **선택** |
| 브라우저 → WebSocket으로 MediaRecorder 조각 전송 | 직접 구현할 게 많고 지연·호환성이 불안정 |

- **이유:** 요구사항이 "PC 화면 공유 + 탭/시스템 소리 + 마이크", "휴대폰 카메라 + 마이크"를 **브라우저에서** 송출하는 것이라, 브라우저가 기본 지원하는 WebRTC가 가장 직접적입니다. 지연이 1초 안팎이고, WHIP/WHEP는 HTTP POST 한 번으로 연결을 맺는 표준이라 구현이 단순합니다.
- **대가:** 미디어가 서버의 UDP/TCP 8189로 직접 오가야 해서, 포트를 열 수 있는 VM이 필요합니다(일반 웹 호스팅·HTTP 터널로는 다른 망의 시청자가 받을 수 없음). 화질 변환(트랜스코딩)은 하지 않습니다.

### 3.2 미디어 서버: MediaMTX

| 선택지 | 판단 |
|---|---|
| WebRTC SFU 직접 구현 | 마감 안에 불가능 |
| LiveKit, Janus, mediasoup | 기능은 많지만 자체 시그널링·SDK를 써야 해서 구성이 무거움 |
| SRS | WHIP/WHEP 지원, 다만 인증 훅·설정이 우리 용도에 비해 복잡 |
| **MediaMTX** | **선택** |

- **이유:** 단일 바이너리(공식 Docker 이미지)로 WHIP/WHEP를 바로 제공합니다. 송출마다 외부 HTTP로 인증을 묻는 기능(`authMethod: http`), 송출 시작·끝에 명령을 실행하는 훅(`runOnAvailable`/`runOnUnavailable`), 세션을 끊는 Control API가 있어서 **송출 통제를 백엔드에 맡길 수 있습니다.** 실기기 테스트 키트(`studio.html`, `watch.html`)로 PC·모바일 송출과 다른 기기 시청을 먼저 검증한 뒤 채택했습니다.
- **대가:** 확장(여러 대 중계)은 고려하지 않았습니다. 최신 버전에서 훅 이름이 바뀌는 등(`runOnReady` → `runOnAvailable`) 버전 변화에 따라가야 합니다.

### 3.3 인증: 세션 쿠키 + CSRF

| 선택지 | 판단 |
|---|---|
| JWT (Authorization 헤더 또는 쿠키) | 만료·재발급·무효화 목록을 직접 만들어야 함 |
| **Spring Security 세션 쿠키 (`HttpOnly`, `Secure`, `SameSite=Lax`)** | **선택** |

- **이유:** Caddy 뒤에서 프론트와 API가 **같은 도메인**이라 쿠키가 가장 단순하고, 로그아웃하면 서버 세션이 즉시 무효화됩니다. `HttpOnly`라 스크립트가 세션을 읽을 수 없습니다.
- **CSRF:** 쿠키 인증은 다른 사이트가 사용자의 쿠키로 요청을 보내는 공격(CSRF)에 노출되므로, `CookieCsrfTokenRepository`로 `XSRF-TOKEN` 쿠키를 내려주고 변경 요청마다 `X-XSRF-TOKEN` 헤더를 요구합니다. MediaMTX가 부르는 `/internal/*`만 CSRF에서 빼고, 대신 Caddy에서 외부 접근을 막습니다.
- **CORS:** 같은 도메인이라 열지 않았습니다. 로컬 개발에서는 Next.js rewrites가 `/api`를 백엔드로 넘깁니다.
- **에러 형식:** 401·403·404·409를 모두 Spring 표준 `ProblemDetail`(RFC 9457)로 응답합니다.
- **심사용 테스트 계정:** 앱 시작 시 `demo-owner`, `demo-viewer`를 만들고, 비밀번호는 `DEMO_PASSWORD` 환경변수로 받습니다. `POST /api/auth/demo-login`으로 비밀번호 없이 로그인할 수 있습니다.
- **대가:** 세션이 서버 메모리에 있어 서버를 재시작하면 다시 로그인해야 합니다(방송 정보는 DB에 남음).

### 3.4 송출 통제: 송출 토큰 + MediaMTX 인증 훅

- **문제:** 송출은 브라우저가 MediaMTX로 직접 보내므로, 백엔드 API의 인가만으로는 "남의 방송 경로로 송출"을 막을 수 없습니다.
- **선택:** 소유자만 받을 수 있는 **송출 토큰**(32바이트 난수, 10분, 방송 1개 전용)을 발급하고, MediaMTX가 송출 요청마다 백엔드에 토큰을 확인합니다. 토큰이 없거나, 다른 방송의 토큰이거나, 만료됐거나, 방송이 ENDED면 거절합니다.
- **방송 경로(`streamPath`)는 UUID**라 추측할 수 없습니다. 시청은 경로만 알면 되므로 공개합니다.
- **토큰 저장:** 서버 메모리(`ConcurrentHashMap`)에 둡니다. 앱을 1대만 운영하므로 충분하고, 재시작하면 소유자가 다시 발급받으면 됩니다(코드에 `shortcut:` 주석으로 표시).
- **상태 이벤트는 쿼리 파라미터:** MediaMTX 훅은 쉘 없이 명령을 실행해서 JSON 본문의 따옴표 처리가 까다롭습니다. 그래서 `wget --post-data=x .../events?type=ready&path=$MTX_PATH`처럼 쿼리 파라미터로 받습니다.

### 3.5 방송 상태: 1회용 방송 + 30초 재연결 유예

| 선택지 | 판단 |
|---|---|
| 채널처럼 계속 재사용 (ENDED → 다시 LIVE) | "방송 중/종료 구분", 녹화·다시보기와 맞추기 어려움 |
| **방송 1건 = 송출 1회 (`READY → LIVE → ENDED`, ENDED는 끝)** | **선택** |

- **끊김 처리:** 모바일 LTE는 잠깐씩 끊깁니다. 끊기자마자 ENDED로 바꾸면 시연·심사 중에 방송이 영영 끝나 버리므로, 끊긴 시각(`disconnected_at`)만 기록하고 **30초 유예**합니다. 그 안에 다시 송출되면 LIVE를 이어가고, 아니면 5초마다 도는 스케줄러가 ENDED로 바꿉니다. 소유자가 직접 종료하면 유예 없이 즉시 ENDED입니다.
- **서버 재시작:** 앱이 뜰 때 LIVE인 방송마다 MediaMTX에 실제로 송출 중인지 물어보고, 아니면 유예를 시작합니다. DB와 실제 송출 상태가 어긋나지 않게 하기 위해서입니다.
- **상태 판단의 기준은 서버:** 시청 화면은 WebRTC 연결이 끊겨도 스스로 "종료"로 판단하지 않고, 서버 상태를 따릅니다.
- **로그:** 상태가 바뀔 때마다 `방송 {id} 상태 A → B (원인: ...)`를 INFO로 남깁니다.
- **구현:** 상태 전환은 엔티티 메서드(`markLive`, `markDisconnected`, `end`)에 모으고 단위 테스트([`BroadcastTest`](../backend/src/test/java/com/streamly/broadcast/BroadcastTest.java))로 확인합니다.

### 3.6 규모: 앱 1대, Kafka·Redis 없음

| 선택지 | 판단 |
|---|---|
| 처음 뼈대: Redis(시청자 수·채팅 Pub/Sub) + Kafka(이벤트) + 앱 여러 대 | 트래픽 급증 대응은 평가 기준 밖. 구성과 장애 지점만 늘어남 |
| **앱 1대 + PostgreSQL** | **선택** |

- **이유:** 평가 기준은 필수 기능 완성도, 인증·인가, 방송 상태 관리입니다. 1대로 정하면 세션·송출 토큰을 메모리에 둘 수 있어 코드가 줄어듭니다.
- **대가:** 수평 확장이 필요해지면 세션 저장소와 송출 토큰을 DB나 Redis로 옮겨야 합니다.

### 3.7 DB 스키마: Flyway

- `ddl-auto: update`는 컬럼 삭제·변경을 반영하지 못하고 운영 DB가 조용히 어긋납니다. **Flyway 마이그레이션**으로 스키마 변경을 SQL 파일로 남기고, 앱은 `ddl-auto: validate`로 엔티티와 스키마가 맞는지만 확인합니다. 머지된 마이그레이션은 고치지 않고 새 파일을 추가합니다.

### 3.8 백엔드 구조

- 패키지는 기능별(`auth`, `broadcast`, `media`, `common`), 기능 안은 `Controller → Service → Repository` 3층만 씁니다. 1인 개발·짧은 마감이라 별도 도메인 계층이나 포트·어댑터는 두지 않았습니다.
- 예외는 MediaMTX 호출 하나: `MediaServerClient` 인터페이스 뒤에 두어 테스트에서 가짜 구현으로 바꿉니다.
- DTO는 `record`로 만듭니다.

### 3.9 HTTPS와 라우팅: Caddy + sslip.io

| 선택지 | 판단 |
|---|---|
| nginx + certbot | 인증서 발급·갱신 설정을 따로 해야 함 |
| 도메인 구매 + Cloudflare | 비용·DNS 설정 시간. Cloudflare 프록시는 WebRTC UDP를 통과시키지 않음 |
| **Caddy + sslip.io** | **선택** |

- **이유:** 화면 공유와 카메라는 **HTTPS에서만** 동작합니다. sslip.io는 `1-2-3-4.sslip.io`가 IP `1.2.3.4`를 가리키는 무료 DNS라 도메인 없이 IP만으로 주소가 생기고, Caddy는 설정 몇 줄로 인증서를 자동 발급·갱신합니다. 라우팅(`/api`, WHIP/WHEP, 나머지)과 내부 경로 차단도 같은 파일에서 합니다.
- **운영 주소:** 처음엔 sslip.io(`34-47-103-199.sslip.io`)로 배포했고, 기억하기 쉬운 주소를 위해 무료 DNS인 **DuckDNS**(`streamly-live.duckdns.org`)로 바꿨습니다. `.env`의 `DOMAIN`만 바꾸면 Caddy가 새 인증서를 받습니다. 직접 배포할 때의 기본값은 여전히 sslip.io입니다.

### 3.10 프론트엔드: Next.js, 브라우저 중심

| 선택지 | 판단 |
|---|---|
| Vite + React (정적 파일을 Caddy가 서빙) | 더 단순하지만 뼈대·CI·이슈가 Next.js 기준이라 바꾸는 비용이 큼 |
| **Next.js (React + TypeScript)** | **선택** |

- 송출·시청은 브라우저 API(카메라, 화면 공유, WebRTC)라 모든 화면을 클라이언트 컴포넌트로 만들고, 데이터도 브라우저에서 `/api`를 호출합니다. 서버 렌더링을 쓰지 않아 세션 쿠키를 Next.js 서버가 대신 넘기는 복잡함이 없습니다. 배포는 `output: 'standalone'` 컨테이너입니다.
- **API 타입은 손으로 쓰지 않습니다.** 백엔드 OpenAPI 스펙에서 생성한 `schema.d.ts`만 쓰고, CI가 매번 다시 생성해서 백엔드와 어긋나면 타입 검사에서 실패합니다.
- **송출 로직** ([`webrtc.ts`](../frontend/src/lib/webrtc.ts), [`studio/page.tsx`](../frontend/src/app/broadcasts/[id]/studio/page.tsx))은 테스트 키트에서 실기기로 검증한 방식을 옮겼습니다.
  - PC: `getDisplayMedia`(탭/시스템 소리 포함) + 마이크를 Web Audio로 섞어 오디오 트랙 하나로 보냄
  - H.264 `packetization-mode=1` 우선: mode 0은 화면 공유처럼 큰 프레임을 쪼개지 못해 시청 쪽 디코딩이 깨질 수 있음
  - ICE 후보 수집을 마친 뒤 SDP를 한 번에 POST (WHIP 트리클 불필요)
  - 모바일 카메라 전환: 휴대폰은 카메라 두 개를 동시에 못 여는 경우가 많아, 기존 카메라를 먼저 끄고 새 카메라를 연 뒤 `replaceTrack`으로 끊김 없이 교체

### 3.11 서버: OCI → GCP

| 선택지 | 판단 |
|---|---|
| OCI Always Free (ARM 2 OCPU / 12GB) | 첫 선택. 인스턴스 생성이 늦어져 보류 |
| **GCP Compute Engine (서울, e2-medium 2 vCPU / 4GB)** | **선택** (신규 계정 무료 체험 크레딧) |
| AWS (t4g.small 2GB) | 대안. 메모리가 빠듯 |
| Render·Railway·Vercel 같은 웹 호스팅 | WebRTC용 UDP 포트를 열 수 없어 제외 |

- 모든 Docker 이미지(Temurin, Node, PostgreSQL, Caddy, MediaMTX)가 ARM·x86을 모두 지원해서 클라우드를 바꿔도 그대로 빌드됩니다.
- [`deploy/setup-server.sh`](../deploy/setup-server.sh) 한 번으로 서버 내부 방화벽 개방, Docker 설치, 코드 받기, `.env` 생성, 실행까지 합니다. 다시 실행하면 업데이트 배포가 됩니다.

## 4. 검증 방법

| 무엇을 | 어떻게 |
|---|---|
| 인가 규칙 (정답 기준) | [`AuthorizationAcceptanceTest`](../backend/src/test/java/com/streamly/acceptance/AuthorizationAcceptanceTest.java): 비로그인·소유자·다른 사용자별 허용/거절, 송출 인증 훅, 종료 후 재송출 금지. 사람이 승인하고 에이전트는 수정 불가 |
| API·DB | `@IntegrationTest`: 실제 서버를 띄우고 Testcontainers PostgreSQL + Flyway로 검증. MediaMTX는 가짜 구현 |
| 상태 전환 | `BroadcastTest` 단위 테스트 |
| 프론트 | CI에서 OpenAPI 타입 생성 → 타입 검사 → lint → build |
| 운영 구성 | 운영용 compose를 로컬에서 그대로 실행: HTTPS, 라우팅, `/internal`·`/actuator` 차단, WHIP 토큰 거절/허용, 상태 이벤트, Secure 쿠키 확인 |
| 실제 송출·시청 | 운영 compose + Cloudflare 터널로 실기기 점검: PC 화면+탭 소리+마이크 → 휴대폰 시청, 휴대폰 카메라(전환 포함) → PC 시청, 종료, 다른 사용자 권한 |

모든 변경은 PR로 들어가고 CI(`backend`, `frontend`, `guard`)를 통과해야 머지됩니다. `guard`는 에이전트가 인수 테스트·CI 설정·CLAUDE.md를 고치지 못하게 검사합니다.

## 5. 범위 밖으로 둔 것

- 수평 확장, 트래픽 급증 대응, 화질 변환(트랜스코딩)
- 실시간 채팅, 시청자 수 (요구사항에 없음. 선택 기능의 댓글은 일반 CRUD)
- Firefox, 맥 Safari의 PC 송출
- 선택 기능(댓글, 도네이션, 녹화·다시보기): 필수 기능, README, 시연 영상을 마친 뒤에만 착수
