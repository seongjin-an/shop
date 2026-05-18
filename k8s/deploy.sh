#!/bin/bash
# 전체 배포 스크립트
# 사용법: ECR_REGISTRY=<account>.dkr.ecr.ap-northeast-2.amazonaws.com IMAGE_TAG=latest ./deploy.sh

set -e

ECR_REGISTRY=${ECR_REGISTRY:?필수: ECR_REGISTRY를 환경변수로 설정하세요}
IMAGE_TAG=${IMAGE_TAG:-latest}
K8S_DIR="$(cd "$(dirname "$0")" && pwd)"

apply() {
  # ${ECR_REGISTRY}, ${IMAGE_TAG} 변수를 치환 후 apply
  sed -e "s|\${ECR_REGISTRY}|${ECR_REGISTRY}|g" \
      -e "s|\${IMAGE_TAG}|${IMAGE_TAG}|g" \
      "$1" | kubectl apply -f -
}

echo "=== 1. Namespace & Secrets ==="
kubectl apply -f "${K8S_DIR}/00-namespace.yaml"
kubectl apply -f "${K8S_DIR}/01-secrets.yaml"

echo ""
echo "=== 2. 인프라 (MySQL, Redis, Kafka) ==="
kubectl apply -f "${K8S_DIR}/infra/mysql.yaml"
kubectl apply -f "${K8S_DIR}/infra/redis.yaml"
kubectl apply -f "${K8S_DIR}/infra/kafka.yaml"

echo "MySQL/Kafka 준비 대기 (60초)..."
sleep 60
kubectl rollout status statefulset/mysql -n shop --timeout=120s
kubectl rollout status statefulset/kafka -n shop --timeout=180s

echo ""
echo "=== 3. Kafka 토픽 생성 ==="
kubectl apply -f "${K8S_DIR}/kafka-connect/init-job.yaml"
kubectl wait --for=condition=complete job/kafka-topics-init -n shop --timeout=180s

echo ""
echo "=== 4. Kafka Connect + Debezium ==="
kubectl apply -f "${K8S_DIR}/kafka-connect/deployment.yaml"
kubectl rollout status deployment/kafka-connect -n shop --timeout=120s

echo "Debezium 커넥터 등록..."
kubectl apply -f "${K8S_DIR}/kafka-connect/init-job.yaml"
kubectl wait --for=condition=complete job/debezium-connector-init -n shop --timeout=120s

echo ""
echo "=== 5. Observability 스택 ==="
kubectl apply -f "${K8S_DIR}/observability/otel-collector.yaml"
kubectl apply -f "${K8S_DIR}/observability/prometheus.yaml"
kubectl apply -f "${K8S_DIR}/observability/loki.yaml"
kubectl apply -f "${K8S_DIR}/observability/tempo.yaml"
kubectl apply -f "${K8S_DIR}/observability/grafana.yaml"

echo ""
echo "=== 6. OTel Operator & Instrumentation CR ==="
echo "cert-manager와 OTel Operator가 설치되어 있지 않으면 먼저 실행:"
echo "  bash ${K8S_DIR}/observability/install.sh"
kubectl apply -f "${K8S_DIR}/observability/instrumentation.yaml"

echo ""
echo "=== 7. 애플리케이션 배포 ==="
for APP in shop-user shop-product shop-stock shop-order shop-payment shop-gateway shop-frontend; do
  apply "${K8S_DIR}/apps/${APP}.yaml"
done

echo ""
echo "=== 8. Ingress 적용 ==="
kubectl apply -f "${K8S_DIR}/apps/ingress.yaml"

echo ""
echo "=== 배포 완료 ==="
echo ""
echo "ALB DNS 확인:"
echo "  kubectl get ingress -n shop"
echo ""
echo "Pod 상태 확인:"
echo "  kubectl get pods -n shop"
echo ""
echo "Grafana 접근: http://<ALB-DNS>/grafana  (admin/admin)"
