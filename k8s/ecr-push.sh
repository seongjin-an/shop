#!/bin/bash
# ECR 이미지 빌드 & 푸시 스크립트
# 사용법: AWS_ACCOUNT_ID=123456789012 AWS_REGION=ap-northeast-2 ./ecr-push.sh [태그]
# 예시:   AWS_ACCOUNT_ID=123456789012 ./ecr-push.sh v1.0.0

set -e

AWS_ACCOUNT_ID=${AWS_ACCOUNT_ID:?필수: AWS_ACCOUNT_ID를 환경변수로 설정하세요}
AWS_REGION=${AWS_REGION:-ap-northeast-2}
IMAGE_TAG=${1:-latest}
ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

SERVICES=(shop-user shop-product shop-stock shop-order shop-payment shop-gateway shop-frontend)
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "=== ECR 로그인 ==="
aws ecr get-login-password --region "${AWS_REGION}" \
  | docker login --username AWS --password-stdin "${ECR_REGISTRY}"

for SERVICE in "${SERVICES[@]}"; do
  REPO="${ECR_REGISTRY}/shop/${SERVICE}"

  echo ""
  echo "=== ${SERVICE} ==="

  # ECR 레포지토리 생성 (이미 존재하면 무시)
  aws ecr describe-repositories --repository-names "shop/${SERVICE}" \
    --region "${AWS_REGION}" > /dev/null 2>&1 \
    || aws ecr create-repository --repository-name "shop/${SERVICE}" \
         --region "${AWS_REGION}" > /dev/null

  # 빌드 & 푸시
  docker build -t "${REPO}:${IMAGE_TAG}" "${ROOT_DIR}/${SERVICE}"
  docker push "${REPO}:${IMAGE_TAG}"
  echo "Pushed: ${REPO}:${IMAGE_TAG}"
done

echo ""
echo "=== 모든 이미지 푸시 완료 ==="
echo ""
echo "k8s 매니페스트의 \${ECR_REGISTRY}와 \${IMAGE_TAG}를 치환하여 배포:"
echo "  ECR_REGISTRY=${ECR_REGISTRY} IMAGE_TAG=${IMAGE_TAG} ./k8s/deploy.sh"
