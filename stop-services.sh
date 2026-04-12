#!/bin/bash
# shop-order / shop-stock / shop-payment 프로세스 종료 스크립트
# 사용법: ./stop-services.sh

# Gradle wrapper → Spring Boot JVM (자식) 구조이므로
# wrapper PID 를 찾아 자식까지 함께 종료

stop_service() {
  local SERVICE=$1
  # wrapper 프로세스: 경로에 서비스명이 포함되어 있음
  local PIDS
  PIDS=$(pgrep -f "${SERVICE}/gradle/wrapper" 2>/dev/null)

  if [ -z "$PIDS" ]; then
    echo "  - ${SERVICE} 실행 중 아님"
    return
  fi

  echo "$PIDS" | while read -r PID; do
    # 1) 자식 프로세스(Spring Boot JVM) 먼저 종료
    pkill -P "$PID" 2>/dev/null
    # 2) Gradle wrapper 종료
    kill "$PID" 2>/dev/null
  done
  echo "  ✓ ${SERVICE} 종료"
}

echo ">>> shop 서비스 종료"
stop_service "shop-order"
stop_service "shop-stock"
stop_service "shop-payment"
echo "완료"
