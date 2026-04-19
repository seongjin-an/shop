#!/bin/bash
# 사용법: ./stop-one.sh shop-order

SERVICE=$1

if [ -z "$SERVICE" ]; then
  echo "사용법: $0 <service-name>"
  exit 1
fi

# Application 클래스 매핑 (핵심)
case "$SERVICE" in
  shop-user) APP="ShopUserApplication" ;;
  shop-product) APP="ShopProductApplication" ;;
  shop-stock) APP="ShopStockApplication" ;;
  shop-order) APP="ShopOrderApplication" ;;
  shop-payment) APP="ShopPaymentApplication" ;;
  *)
    echo "✗ 알 수 없는 서비스: $SERVICE"
    exit 1
    ;;
esac

PIDS=$(pgrep -f "$APP")

if [ -z "$PIDS" ]; then
  echo "⚠ 실행중 아님: $SERVICE"
  exit 0
fi

echo ">>> $SERVICE 종료 시도 (PID: $PIDS)"

# graceful shutdown
kill $PIDS

sleep 3

# 아직 살아있으면 강제 종료
if pgrep -f "$APP" >/dev/null; then
  echo "⚠ 강제 종료 진행"
  pkill -9 -f "$APP"
fi

echo "✓ 종료 완료: $SERVICE"