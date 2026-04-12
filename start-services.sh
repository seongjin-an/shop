#!/bin/bash
# shop-order / shop-stock / shop-payment 백그라운드 기동 스크립트
# 사용법: ./start-services.sh
#
# 콘솔 출력(stdout/stderr)은 /dev/null 로 버림
# → logback FILE appender 가 <service>/logs/app.log 에 기록하므로 중복 불필요
# → nohup.out 생성 없음

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

  # nohup 출력을 /dev/null 로 버려 nohup.out 생성 방지
  # 로그는 logback → logs/app.log 에서 관리
  nohup bash -c "cd '${SERVICE_DIR}' && ./gradlew bootRun -DLOG_PATH='${LOG_PATH}'" \
    > /dev/null 2>&1 &

  echo "  ✓ ${SERVICE} 기동 (pid=$!, logs=${LOG_PATH}/app.log)"
}

echo ">>> shop 서비스 기동"
start_service "shop-stock"
start_service "shop-order"
start_service "shop-payment"
echo "완료 — 로그 확인: tail -f <service>/logs/app.log"
