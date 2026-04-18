#!/bin/bash
# shop-order / shop-stock / shop-payment 백그라운드 기동 스크립트
# 사용법: ./start-services.sh
#
# 현재 쉘(zsh 등) 의 JAVA_HOME 을 자식 bash 에 그대로 전파한다.
# .bash_profile / PATH 는 건드리지 않는다.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ──────────────────────────────────────────
# JAVA_HOME 결정
# 1) 호출 쉘에서 이미 export 돼있으면 그대로 사용
# 2) 없으면 macOS 기본 도구로 Java 21 탐색 (폴백)
# ──────────────────────────────────────────
RESOLVED_JAVA_HOME="${JAVA_HOME:-}"
if [ -z "$RESOLVED_JAVA_HOME" ] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
  RESOLVED_JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
fi

if [ -z "$RESOLVED_JAVA_HOME" ] || [ ! -x "${RESOLVED_JAVA_HOME}/bin/java" ]; then
  echo "✗ JAVA_HOME(21) 을 찾을 수 없습니다."
  echo "  현재 쉘에서 'java -version' 이 21 로 잡히는지 확인하거나,"
  echo "  직접 환경변수로 지정해 실행: JAVA_HOME=/path/to/jdk21 ./start-services.sh"
  exit 1
fi

JAVA_VERSION_LINE="$("${RESOLVED_JAVA_HOME}/bin/java" -version 2>&1 | head -n1)"
echo ">>> 사용 JAVA_HOME : ${RESOLVED_JAVA_HOME}"
echo ">>> ${JAVA_VERSION_LINE}"

start_service() {
  local SERVICE=$1
  local SERVICE_DIR="${SCRIPT_DIR}/${SERVICE}"
  local LOG_PATH="${SERVICE_DIR}/logs"
  local STARTUP_LOG="${LOG_PATH}/startup.log"

  if [ ! -d "$SERVICE_DIR" ]; then
    echo "  ✗ ${SERVICE} 디렉토리 없음: ${SERVICE_DIR}"
    return
  fi

  mkdir -p "$LOG_PATH"

  # JAVA_HOME 만 자식 쉘에 명시적으로 주입 (PATH 는 건드리지 않음).
  # gradlew 스크립트는 JAVA_HOME 이 있으면 $JAVA_HOME/bin/java 를 우선 사용한다.
  # stdout/stderr 는 /dev/null 대신 startup.log 로 → 실패 시 즉시 추적 가능.
  nohup env JAVA_HOME="${RESOLVED_JAVA_HOME}" \
    bash -c "cd '${SERVICE_DIR}' && ./gradlew bootRun" \
    > "${STARTUP_LOG}" 2>&1 &

  local PID=$!
  echo "  ✓ ${SERVICE} 기동 요청 (wrapper pid=${PID})"
  echo "     startup log : ${STARTUP_LOG}"
  echo "     app log     : ${LOG_PATH}/app.log"
}

echo ">>> shop 서비스 기동"
start_service "shop-stock"
start_service "shop-order"
start_service "shop-payment"

# ──────────────────────────────────────────
# 헬스체크: 5초 후 실제로 Shop*Application JVM 이 살아있는지 검증
# ──────────────────────────────────────────
#echo ""
#echo ">>> 5초 대기 후 프로세스 확인..."
#sleep 5
#
#check_alive() {
#  local APP_CLASS=$1
#  local SERVICE=$2
#  if pgrep -f "${APP_CLASS}" >/dev/null 2>&1; then
#    echo "  ✓ ${SERVICE} JVM 살아있음"
#  else
#    echo "  ✗ ${SERVICE} JVM 없음 → tail -n 50 ${SCRIPT_DIR}/${SERVICE}/logs/startup.log"
#  fi
#}
#
#check_alive "ShopStockApplication"   "shop-stock"
#check_alive "ShopOrderApplication"   "shop-order"
#check_alive "ShopPaymentApplication" "shop-payment"

echo ""
echo "완료 — 애플리케이션 로그: tail -f <service>/logs/app.log"
echo "       기동 실패 시: tail -f <service>/logs/startup.log"
