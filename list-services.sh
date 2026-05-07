#!/bin/bash

echo ">>> Shop 서비스 프로세스 목록"
echo ""

printf "%-15s %-10s %-20s\n" "SERVICE" "PID" "STATUS"
echo "--------------------------------------------------"

check_service() {
  local SERVICE_NAME=$1
  local KEYWORD=$2

  PID=$(pgrep -f "$KEYWORD" | head -n 1)

  if [ -n "$PID" ]; then
    printf "%-15s %-10s %-20s\n" "$SERVICE_NAME" "$PID" "RUNNING"
  else
    printf "%-15s %-10s %-20s\n" "$SERVICE_NAME" "-" "STOPPED"
  fi
}
check_service "shop-gateway" "ShopGatewayApplication"
check_service "shop-user" "ShopUserApplication"
check_service "shop-product" "ShopProductApplication"
check_service "shop-stock" "ShopStockApplication"
check_service "shop-order" "ShopOrderApplication"
check_service "shop-payment" "ShopPaymentApplication"

echo ""