#!/bin/bash
# 사용법: ./start-one.sh shop-order

SERVICE=$1

if [ -z "$SERVICE" ]; then
  echo "사용법: $0 <service-name>"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SERVICE_DIR="${SCRIPT_DIR}/${SERVICE}"
LOG_PATH="${SERVICE_DIR}/logs"
STARTUP_LOG="${LOG_PATH}/startup.log"

# JAVA_HOME 처리 (기존과 동일)
RESOLVED_JAVA_HOME="${JAVA_HOME:-}"
if [ -z "$RESOLVED_JAVA_HOME" ] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
  RESOLVED_JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
fi

if [ -z "$RESOLVED_JAVA_HOME" ]; then
  echo "✗ JAVA_HOME 없음"
  exit 1
fi

# 이미 실행중인지 체크
if pgrep -f "$SERVICE" >/dev/null 2>&1; then
  echo "⚠ 이미 실행중: $SERVICE"
  exit 0
fi

mkdir -p "$LOG_PATH"

nohup env JAVA_HOME="${RESOLVED_JAVA_HOME}" \
  bash -c "cd '${SERVICE_DIR}' && ./gradlew bootRun" \
  > "${STARTUP_LOG}" 2>&1 &

PID=$!

echo "✓ $SERVICE 기동 (wrapper pid=$PID)"
echo "  log: $STARTUP_LOG"