#!/usr/bin/env bash
# Debezium MySQL Outbox 커넥터 등록 스크립트
# docker compose up -d 후 1회 실행

set -e

CONNECT_URL="http://localhost:28083"
CONNECTOR_NAME="shop-outbox-connector"

echo "Kafka Connect 준비 대기 중..."
until curl -sf "${CONNECT_URL}/connectors" > /dev/null; do
  sleep 2
done
echo "Kafka Connect 준비 완료."

# 기존 커넥터 있으면 삭제 후 재등록
if curl -sf "${CONNECT_URL}/connectors/${CONNECTOR_NAME}" > /dev/null 2>&1; then
  echo "기존 커넥터 삭제: ${CONNECTOR_NAME}"
  curl -X DELETE "${CONNECT_URL}/connectors/${CONNECTOR_NAME}"
  echo ""
fi

echo "커넥터 등록 중: ${CONNECTOR_NAME}"
curl -X POST "${CONNECT_URL}/connectors" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "'"${CONNECTOR_NAME}"'",
    "config": {
      "connector.class": "io.debezium.connector.mysql.MySqlConnector",
      "database.hostname": "mysql",
      "database.port": "3306",
      "database.user": "root",
      "database.password": "1234",
      "database.server.id": "223344",
      "topic.prefix": "dbz",
      "database.include.list": "shop",
      "table.include.list": "shop.order_outbox_event,shop.stock_outbox_event,shop.payment_outbox_event,shop.product_outbox_event",
      "snapshot.mode": "schema_only",
      "schema.history.internal.kafka.bootstrap.servers": "kafka:9092",
      "schema.history.internal.kafka.topic": "schema-changes.shop",

      "transforms": "outbox",
      "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
      "transforms.outbox.route.by.field": "destination_topic",
      "transforms.outbox.route.topic.replacement": "${routedByValue}",
      "transforms.outbox.table.field.event.key": "partition_key",
      "transforms.outbox.table.field.event.id": "event_id",
      "transforms.outbox.table.field.event.payload": "payload",
      "transforms.outbox.table.op.invalid.behavior": "warn"
    }
  }'

echo ""
echo "커넥터 등록 완료. 상태 확인:"
sleep 2
curl -s "${CONNECT_URL}/connectors/${CONNECTOR_NAME}/status" | python3 -m json.tool 2>/dev/null || \
  curl -s "${CONNECT_URL}/connectors/${CONNECTOR_NAME}/status"
echo ""
