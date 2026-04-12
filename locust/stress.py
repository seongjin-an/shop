import random
import json
from locust import FastHttpUser, task, between, events
from locust.runners import MasterRunner, WorkerRunner

# README.md Locust 섹션에 명시된 상품 정보 기반
# 실제 DB에 등록된 productId로 교체하여 사용하세요
PRODUCTS = [
    {
        "productId": "019d80cf-ca24-7ee5-8f1e-93af7e4ea77c",
        "productName": "VitaminA",
        "unitPrice": 30000,
    },
]

USERS = [1]  # 테스트용 userId 목록

DELIVERY_ADDRESSES = [
    "서울시 강남구 테헤란로 1",
    "서울시 종로구 세종대로 1",
    "부산시 해운대구 해운대로 1",
]


def build_order_payload() -> dict:
    """랜덤하게 주문 페이로드를 생성합니다."""
    product = random.choice(PRODUCTS)
    return {
        "userId": random.choice(USERS),
        "deliveryAddress": random.choice(DELIVERY_ADDRESSES),
        "items": [
            {
                "productId": product["productId"],
                "productName": product["productName"],
                "quantity": random.randint(1, 3),
                "unitPrice": product["unitPrice"],
            }
        ],
    }


class OrderUser(FastHttpUser):
    """
    shop-order (8082) 에 직접 주문을 생성하여 Saga 전체 흐름을 부하 테스트합니다.

    실행 예:
        source .venv/bin/activate
        locust -f stress.py --host http://localhost:8082 \\
               --users 50 --spawn-rate 5 --run-time 60s --headless
    """

    host = "http://localhost:8082"
    wait_time = between(1, 3)  # 태스크 사이 1~3초 대기

    # ------------------------------------------------------------------ #
    # 태스크 weight: 숫자가 높을수록 더 자주 호출됩니다.                      #
    # ------------------------------------------------------------------ #

    @task(10)
    def create_order(self):
        """POST /orders — 주문 생성 (Saga 트리거)"""
        payload = build_order_payload()
        with self.client.post(
            "/orders",
            json=payload,
            headers={"Content-Type": "application/json"},
            catch_response=True,
        ) as resp:
            if resp.status_code == 201:
                # Location 헤더에서 orderId 추출 후 상태 조회에 활용
                location = resp.headers.get("Location", "")
                order_id = location.split("/")[-1] if location else None
                if order_id:
                    # 인스턴스 변수에 저장해 두면 get_order_status 에서 재활용 가능
                    if not hasattr(self, "_order_ids"):
                        self._order_ids = []
                    self._order_ids.append(order_id)
                    # 최근 20개만 유지
                    self._order_ids = self._order_ids[-20:]
                resp.success()
            elif resp.status_code in (400, 422):
                # 잘못된 요청은 실패로 표시하지 않음 (의도된 케이스 구분용)
                resp.failure(f"Bad request: {resp.status_code}")
            else:
                resp.failure(f"Unexpected status: {resp.status_code}")

#     @task(3)
#     def get_order_status(self):
#         """GET /orders/{orderId} — 주문 상태 폴링 (프론트 3초 폴링 재현)"""
#         order_ids = getattr(self, "_order_ids", [])
#         if not order_ids:
#             return  # 아직 생성된 주문이 없으면 스킵
#
#         order_id = random.choice(order_ids)
#         with self.client.get(
#             f"/orders/{order_id}",
#             name="/orders/[orderId]",  # 통계를 하나의 버킷으로 집계
#             catch_response=True,
#         ) as resp:
#             if resp.status_code == 200:
#                 resp.success()
#             elif resp.status_code == 404:
#                 resp.failure("Order not found")
#             else:
#                 resp.failure(f"Unexpected status: {resp.status_code}")


# ------------------------------------------------------------------ #
# 이벤트 훅: 실행 시작 / 종료 로그                                        #
# ------------------------------------------------------------------ #

@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    print("\n[Locust] ▶ Stress test started")
    print(f"[Locust]   Target host : {environment.host}")
    print(f"[Locust]   Products    : {[p['productName'] for p in PRODUCTS]}")


@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    print("\n[Locust] ■ Stress test finished")
