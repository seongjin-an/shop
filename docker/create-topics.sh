#!/bin/bash
# Kafka 토픽 생성 스크립트
# 실행: ./docker/create-topics.sh
# 전제: docker compose up -d 로 kafka 컨테이너가 실행 중이어야 합니다.

KAFKA_CONTAINER="kafka"
BOOTSTRAP="localhost:9092"
PARTITIONS=3
REPLICATION=1

TOPICS=(
  # shop-product → shop-stock
  "product-created"

  # shop-order → shop-stock
  "order-created"

  # shop-stock → shop-order
  "stock-reserved"
  "stock-reserve-failed"

  # shop-order → payment (추후)
  "payment-requested"

  # payment → shop-order / shop-stock (추후)
  "payment-success"
  "payment-failed"

  # shop-order → shop-stock (보상 트랜잭션, 추후)
  "order-canceled"
)

echo ">>> Kafka 토픽 생성 시작 (container: $KAFKA_CONTAINER)"
echo ""

for TOPIC in "${TOPICS[@]}"; do
  # 주석 라인 건너뜀
  [[ "$TOPIC" =~ ^# ]] && continue
  [[ -z "$TOPIC" ]] && continue

  docker exec "$KAFKA_CONTAINER" \
    kafka-topics --bootstrap-server "$BOOTSTRAP" \
    --create \
    --if-not-exists \
    --topic "$TOPIC" \
    --partitions "$PARTITIONS" \
    --replication-factor "$REPLICATION"

  if [ $? -eq 0 ]; then
    echo "  ✓ $TOPIC"
  else
    echo "  ✗ $TOPIC (실패)"
  fi
done

echo ""
echo ">>> 생성된 토픽 목록:"
docker exec "$KAFKA_CONTAINER" \
  kafka-topics --bootstrap-server "$BOOTSTRAP" --list | grep -v "^__" | sort
