# streamly

치지직 같은 라이브 스트리밍 서비스. 사람의 판단을 검증 가능한 신호로 바꾸고, 에이전트가 그 신호를 기준으로 스스로 고치는 개발 파이프라인 위에서 만든다.

## 자동 수정 루프 1 (코드 레벨)

| 항목 | 구현 |
|---|---|
| 감지 신호 | `ci.yml`: 포맷(Spotless), 테스트(Testcontainers), OpenAPI → 프론트 타입 검사, 보호 규칙(guard) |
| 정답 기준 | 위 검사가 모두 통과. `acceptance/` 인수 테스트는 사람이 직접 커밋하고, 에이전트는 수정할 수 없음 |
| 되돌리기 | 모든 작업은 PR 브랜치에서만. main은 브랜치 보호 |
| 멈춤 조건 | `autofix.yml`: 최대 3회 (라벨 `autofix-1..3`). 넘기거나 Claude가 고치지 못하면 `needs-human` 라벨과 이슈 생성 |

성공하면 `autofix-succeeded` 라벨이 붙는다. 라벨로 자동 수정 성공률을 집계할 수 있다.

## 처음 세팅 (순서대로)

1. **GitHub에 빈 레포 만들기**
2. **CODEOWNERS 수정**: `.github/CODEOWNERS` 의 `YOUR_GITHUB_ID` 를 본인 아이디로 바꾸기 (앞에 `@` 유지)
3. **포맷 맞추고 push**
   ```bash
   cd backend && ./gradlew spotlessApply && cd ..
   git init -b main && git add . && git commit -m "chore: 프로젝트 초기 세팅"
   git remote add origin https://github.com/<아이디>/<레포>.git
   git push -u origin main
   ```
4. **Claude GitHub App 설치**: https://github.com/apps/claude 에서 이 레포에 설치
5. **토큰 등록**: 터미널에서 `claude setup-token` 실행 → 나온 토큰을 레포 Settings → Secrets and variables → Actions 에 `CLAUDE_CODE_OAUTH_TOKEN` 이름으로 추가
6. **main 브랜치 보호** (Settings → Branches → Add rule, `main`)
   - Require a pull request before merging
   - Require status checks to pass: `backend`, `frontend`, `guard`
   - Require review from Code Owners
   - 1인 개발이라 본인 PR은 본인이 승인할 수 없으므로, 관리자 우회(bypass)는 허용해 둔다

## 루프가 도는지 확인하기

일부러 포맷을 깨뜨린 PR을 올려서 Claude가 고치는지 본다.

```bash
git checkout -b test/autofix
# StreamlyApplication.java 의 들여쓰기를 망가뜨린다
sed -i 's/^  public static/      public static/' backend/src/main/java/com/streamly/StreamlyApplication.java
git commit -am "test: 자동 수정 루프 확인" && git push -u origin test/autofix
gh pr create --fill
```

기대 결과: CI 실패 → Autofix 실행 → `autofix-1` 라벨 → `fix(autofix): ...` 커밋 → CI 통과 → `autofix-succeeded` 라벨.

## 로컬 개발

```bash
docker compose -f infra/docker-compose.yml up -d
cd backend && ./gradlew bootRun
```

에이전트 작업 규칙과 아키텍처 결정은 [CLAUDE.md](CLAUDE.md) 참고.
