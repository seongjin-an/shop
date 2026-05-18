import random
import json
from locust import FastHttpUser, task, between, events

# ──────────────────────────────────────────────────────────────
# 테스트 계정 목록
# 여러 계정을 등록하면 virtual user 마다 랜덤으로 배정됩니다.
# ──────────────────────────────────────────────────────────────
USER_CREDENTIALS = [
    {"username": "admin", "password": "admin"},
]

PRODUCTS = [
    {
        "productId": "019e3979-791a-7ca1-9143-dd531f0987d6",
        "productName": "VitaminA",
        "unitPrice": 30000,
    },
]

DELIVERY_ADDRESSES = [
    "서울시 강남구 테헤란로 1",
    "서울시 종로구 세종대로 1",
    "부산시 해운대구 해운대로 1",
]


def build_order_payload(user_id: int) -> dict:
    product = random.choice(PRODUCTS)
    return {
        "userId": user_id,
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
    Gateway(8090) 를 통해 주문을 생성하여 Saga 전체 흐름을 부하 테스트합니다.
    on_start() 에서 JWT 로그인 후 모든 요청에 Authorization 헤더를 첨부합니다.

    실행 예:
        source .venv/bin/activate
        locust -f stress.py --host http://localhost:8090 \\
               --users 50 --spawn-rate 5 --run-time 60s --headless
    """

    host = "http://localhost:8090"
    wait_time = between(1, 3)

    # ── 초기화 ─────────────────────────────────────────────────

    def on_start(self):
        self._token: str | None = None
        self._user_id: int | None = None
        self._order_ids: list[str] = []
        self._login()

    # ── 인증 헬퍼 ──────────────────────────────────────────────

    def _login(self) -> bool:
        """로그인 후 accessToken 과 userId 를 저장합니다. 성공 여부를 반환합니다."""
        cred = random.choice(USER_CREDENTIALS)
        resp = self.client.post(
            "/user/api/users/login",
            json=cred,
            headers={"Content-Type": "application/json"},
            name="/user/api/users/login",
        )
        if resp.status_code == 200:
            data = resp.json()
            self._token = data.get("accessToken")
            self._user_id = data.get("user", {}).get("userId")
            return True
        else:
            self._token = None
            self._user_id = None
            return False

    def _auth_headers(self) -> dict:
        headers = {"Content-Type": "application/json"}
        if self._token:
            headers["Authorization"] = f"Bearer {self._token}"
        return headers

    # ── 태스크 ─────────────────────────────────────────────────

    @task(10)
    def create_order(self):
        """POST /order/orders — 주문 생성 (Saga 트리거)"""
        if not self._token:
            self._login()
            return

        payload = build_order_payload(self._user_id or 1)
        with self.client.post(
            "/order/orders",
            json=payload,
            headers=self._auth_headers(),
            name="/order/orders",
            catch_response=True,
        ) as resp:
            if resp.status_code == 201:
                location = resp.headers.get("Location", "")
                order_id = location.split("/")[-1] if location else None
                if order_id:
                    self._order_ids.append(order_id)
                    self._order_ids = self._order_ids[-20:]
                resp.success()
            elif resp.status_code == 401:
                # 토큰 만료 → 재로그인 후 다음 태스크에서 재시도
                self._login()
                resp.failure("401 Unauthorized — re-logged in")
            elif resp.status_code in (400, 422):
                resp.failure(f"Bad request: {resp.status_code}")
            else:
                resp.failure(f"Unexpected status: {resp.status_code}")

    @task(3)
    def get_order_status(self):
        """GET /order/orders/{orderId} — 주문 상태 폴링 (프론트 3초 폴링 재현)"""
        if not self._order_ids or not self._token:
            return

        order_id = random.choice(self._order_ids)
        with self.client.get(
            f"/order/orders/{order_id}",
            headers=self._auth_headers(),
            name="/order/orders/[orderId]",
            catch_response=True,
        ) as resp:
            if resp.status_code == 200:
                resp.success()
            elif resp.status_code == 401:
                self._login()
                resp.failure("401 Unauthorized — re-logged in")
            elif resp.status_code == 404:
                resp.failure("Order not found")
            else:
                resp.failure(f"Unexpected status: {resp.status_code}")


# ──────────────────────────────────────────────────────────────
# 이벤트 훅
# ──────────────────────────────────────────────────────────────

@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    print("\n[Locust] ▶ Stress test started")
    print(f"[Locust]   Target host  : {environment.host}  (via gateway)")
    print(f"[Locust]   Products     : {[p['productName'] for p in PRODUCTS]}")
    print(f"[Locust]   Credentials  : {[c['username'] for c in USER_CREDENTIALS]}")


@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    print("\n[Locust] ■ Stress test finished")
