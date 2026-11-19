#!/usr/bin/env bash
# Ubuntu 24.04 서버(OCI, GCP, AWS 등)를 처음 세팅하고 Streamly를 배포한다. 여러 번 실행해도 된다 (업데이트 배포에도 사용).
#
# 서버에서:
#   PUBLIC_IP=1.2.3.4 POSTGRES_PASSWORD=... DEMO_PASSWORD=... bash setup-server.sh
set -euo pipefail

: "${PUBLIC_IP:?PUBLIC_IP 가 필요합니다}"
: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD 가 필요합니다}"
: "${DEMO_PASSWORD:?DEMO_PASSWORD 가 필요합니다}"
REPO=https://github.com/ksoheee/streamly.git
DIR="$HOME/streamly"
DOMAIN="${PUBLIC_IP//./-}.sslip.io"

echo "== 1. 서버 내부 방화벽 열기 (OCI 이미지는 22번 외에 막혀 있다. 다른 클라우드는 영향 없음)"
open_port() {
  sudo iptables -C INPUT "$@" -j ACCEPT 2>/dev/null || sudo iptables -I INPUT 1 "$@" -j ACCEPT
}
open_port -p tcp -m multiport --dports 80,443,8189
open_port -p udp --dport 8189
if command -v netfilter-persistent >/dev/null; then sudo netfilter-persistent save; fi

echo "== 2. Docker 설치"
if ! command -v docker >/dev/null; then
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker "$USER"
fi

echo "== 3. 코드 받기"
if [ -d "$DIR/.git" ]; then git -C "$DIR" pull --ff-only; else git clone "$REPO" "$DIR"; fi

echo "== 4. 설정 (.env)"
cd "$DIR/deploy"
umask 077
cat > .env <<EOF
PUBLIC_IP=$PUBLIC_IP
DOMAIN=$DOMAIN
POSTGRES_PASSWORD=$POSTGRES_PASSWORD
DEMO_PASSWORD=$DEMO_PASSWORD
EOF

echo "== 5. 빌드하고 실행 (처음에는 몇 분 걸린다)"
sudo docker compose up -d --build

echo
echo "완료: https://$DOMAIN"
echo "인증서 발급에 1분쯤 걸릴 수 있다. 로그: cd $DIR/deploy && sudo docker compose logs -f"
