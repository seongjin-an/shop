#!/bin/bash
# shop-order / shop-stock / shop-payment 백그라운드 기동 스크립트
# 사용법: ./start-services.sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

start_service() {
  local SERVICE=$1
  local SERVICE_DIR="${SCRIPT_DIR}/${SERVICE}"
  local LOG_PATH="${SERVICE_DIR}/logs"

  if [ ! -d "$SERVICE_DIR" ]; then
    echo "  ✗ ${SERVICE} 디렉토리 없음: ${SERVICE_DIR}"
    return
  fi

  mkdir -p "$LOG_PATH"

  # -l (login shell) 로 실행 → .bash_profile 로드 → sdkman/JAVA_HOME 초기화됨
  # LOG_PATH 는 build.gradle bootRun 태스크에서 projectDir 기준으로 자동 주입
  # nohup.out 방지: stdout/stderr → /dev/null
  nohup bash -l -c "cd '${SERVICE_DIR}' && ./gradlew bootRun" \
    > /dev/null 2>&1 &

  echo "  ✓ ${SERVICE} 기동 (pid=$!, logs=${LOG_PATH}/app.log)"
}

echo ">>> shop 서비스 기동"
start_service "shop-stock"
start_service "shop-order"
start_service "shop-payment"
echo "완료 — 로그 확인: tail -f <service>/logs/app.log"
