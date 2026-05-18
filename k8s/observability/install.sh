#!/bin/bash
# OTel Operator 설치 스크립트
# 순서: cert-manager → OTel Operator → Instrumentation CR

set -e

echo "=== 1. cert-manager 설치 ==="
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.16.3/cert-manager.yaml

echo "cert-manager 준비 대기..."
kubectl rollout status deployment cert-manager -n cert-manager --timeout=120s
kubectl rollout status deployment cert-manager-webhook -n cert-manager --timeout=120s
kubectl rollout status deployment cert-manager-cainjector -n cert-manager --timeout=120s

echo "=== 2. OpenTelemetry Operator 설치 ==="
kubectl apply -f https://github.com/open-telemetry/opentelemetry-operator/releases/latest/download/opentelemetry-operator.yaml

echo "OTel Operator 준비 대기..."
kubectl rollout status deployment opentelemetry-operator-controller-manager \
  -n opentelemetry-operator-system --timeout=120s

echo "=== 3. Instrumentation CR 적용 ==="
kubectl apply -f "$(dirname "$0")/instrumentation.yaml"

echo ""
echo "설치 완료!"
echo "앱 Deployment의 Pod spec에 아래 annotation을 추가하면 agent가 자동 주입됩니다:"
echo "  annotations:"
echo "    instrumentation.opentelemetry.io/inject-java: \"true\""
