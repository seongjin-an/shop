"""
Saga 정합성 검증 스크립트
=========================
스트레스 테스트 이후 DB 상태를 검증합니다.

실행:
    source .venv/bin/activate
    pip install mysql-connector-python  # 최초 1회
    python check_consistency.py
"""

import sys
from typing import Optional
import mysql.connector

# ──────────────────────────────────────────────
# DB 접속 정보 (CLAUDE.md 기준)
# ──────────────────────────────────────────────
DB_CONFIG = dict(
    host="127.0.0.1",
    port=23306,
    user="dev_user",
    password="dev_password",
    database="shop",
)

# ANSI 색상
GREEN  = "\033[92m"
RED    = "\033[91m"
YELLOW = "\033[93m"
CYAN   = "\033[96m"
BOLD   = "\033[1m"
RESET  = "\033[0m"

PASS = f"{GREEN}✅ PASS{RESET}"
FAIL = f"{RED}❌ FAIL{RESET}"
WARN = f"{YELLOW}⚠️  WARN{RESET}"
INFO = f"{CYAN}ℹ️  INFO{RESET}"

errors: list[str] = []
warnings: list[str] = []


def section(title: str):
    print(f"\n{BOLD}{'─'*60}{RESET}")
    print(f"{BOLD}{CYAN}  {title}{RESET}")
    print(f"{BOLD}{'─'*60}{RESET}")


def ok(msg: str):
    print(f"  {PASS}  {msg}")


def fail(msg: str):
    print(f"  {FAIL}  {msg}")
    errors.append(msg)


def warn(msg: str):
    print(f"  {WARN}  {msg}")
    warnings.append(msg)


def info(msg: str):
    print(f"  {INFO}  {msg}")


# ──────────────────────────────────────────────
# 헬퍼
# ──────────────────────────────────────────────

def q(cur, sql: str, params=None) -> list[dict]:
    cur.execute(sql, params or ())
    cols = [d[0] for d in cur.description]
    return [dict(zip(cols, row)) for row in cur.fetchall()]


def q1(cur, sql: str, params=None) -> Optional[dict]:
    rows = q(cur, sql, params)
    return rows[0] if rows else None


# ══════════════════════════════════════════════
# 검증 함수들
# ══════════════════════════════════════════════

def check_order_status_distribution(cur):
    """1. 주문 상태 분포 — 전체 Saga 흐름을 한눈에"""
    section("1. 주문 상태 분포 (orders)")
    rows = q(cur, """
        SELECT order_status, COUNT(*) AS cnt
        FROM orders
        GROUP BY order_status
        ORDER BY cnt DESC
    """)
    total = sum(r["cnt"] for r in rows)
    info(f"총 주문 수: {total}")
    for r in rows:
        pct = r["cnt"] / total * 100 if total else 0
        print(f"    {r['order_status']:<20} {r['cnt']:>6} 건  ({pct:.1f}%)")

    # PENDING 상태가 오래 남아있으면 Saga가 중간에 멈춘 것
    pending = q1(cur, "SELECT COUNT(*) AS cnt FROM orders WHERE order_status = 'PENDING' AND created_at < NOW() - INTERVAL 30 SECOND")
    if pending and pending["cnt"] > 0:
        warn(f"PENDING 상태가 30초 이상 지난 주문 {pending['cnt']}건 — Saga 처리 지연 또는 Kafka 소비 실패 의심")
    else:
        ok("30초 이상 묵은 PENDING 주문 없음")

    # PAYMENT_FAILED 가 CANCELLED 로 전이 안 된 경우
    stuck = q1(cur, "SELECT COUNT(*) AS cnt FROM orders WHERE order_status = 'PAYMENT_FAILED'")
    if stuck and stuck["cnt"] > 0:
        warn(f"PAYMENT_FAILED 상태 {stuck['cnt']}건 — 아직 보상 트랜잭션(order-canceled) 처리 중이거나 누락")
    else:
        ok("PAYMENT_FAILED 상태 주문 없음 (보상 트랜잭션 완료)")


def check_order_total_amount(cur):
    """2. 주문 금액 정합성 — total_amount == Σ(unit_price × quantity)"""
    section("2. 주문 금액 정합성 (orders ↔ order_item)")
    rows = q(cur, """
        SELECT
            o.order_id,
            o.total_amount,
            SUM(i.unit_price * i.quantity) AS calculated
        FROM orders o
        JOIN order_item i ON i.order_id = o.order_id
        GROUP BY o.order_id, o.total_amount
        HAVING ABS(o.total_amount - SUM(i.unit_price * i.quantity)) > 0.01
        LIMIT 10
    """)
    if rows:
        fail(f"금액 불일치 주문 {len(rows)}건 발견")
        for r in rows:
            print(f"    order_id={r['order_id']}  stored={r['total_amount']}  calc={r['calculated']}")
    else:
        ok("모든 주문의 total_amount가 order_item 합계와 일치")


def check_order_item_orphan(cur):
    """3. 고아 order_item 검사 — 부모 주문이 없는 아이템"""
    section("3. 고아 order_item 검사")
    row = q1(cur, """
        SELECT COUNT(*) AS cnt
        FROM order_item i
        LEFT JOIN orders o ON o.order_id = i.order_id
        WHERE o.order_id IS NULL
    """)
    cnt = row["cnt"] if row else 0
    if cnt > 0:
        fail(f"부모 주문 없는 order_item {cnt}건")
    else:
        ok("고아 order_item 없음")


def check_order_payment_consistency(cur):
    """4. 주문 ↔ 결제 정합성 (Saga 결과 대조)"""
    section("4. 주문 ↔ 결제 정합성 (orders ↔ payments)")

    # COMPLETED 주문 → COMPLETED 결제가 있어야 함
    mismatch_completed = q1(cur, """
        SELECT COUNT(*) AS cnt
        FROM orders o
        LEFT JOIN payments p ON p.saga_id = o.saga_id AND p.status = 'COMPLETED'
        WHERE o.order_status = 'COMPLETED' AND p.payment_id IS NULL
    """)
    if mismatch_completed and mismatch_completed["cnt"] > 0:
        fail(f"COMPLETED 주문인데 COMPLETED 결제가 없는 케이스: {mismatch_completed['cnt']}건")
    else:
        ok("COMPLETED 주문은 모두 COMPLETED 결제를 보유")

    # CANCELLED 주문 → FAILED 결제가 있어야 함
    mismatch_cancelled = q1(cur, """
        SELECT COUNT(*) AS cnt
        FROM orders o
        LEFT JOIN payments p ON p.saga_id = o.saga_id AND p.status = 'FAILED'
        WHERE o.order_status = 'CANCELLED' AND p.payment_id IS NULL
    """)
    if mismatch_cancelled and mismatch_cancelled["cnt"] > 0:
        fail(f"CANCELLED 주문인데 FAILED 결제가 없는 케이스: {mismatch_cancelled['cnt']}건")
    else:
        ok("CANCELLED 주문은 모두 FAILED 결제를 보유")

    # STOCK_FAILED 주문 → 결제 레코드 자체가 없어야 함
    unexpected_payment = q1(cur, """
        SELECT COUNT(*) AS cnt
        FROM orders o
        JOIN payments p ON p.saga_id = o.saga_id
        WHERE o.order_status = 'STOCK_FAILED'
    """)
    if unexpected_payment and unexpected_payment["cnt"] > 0:
        fail(f"STOCK_FAILED 주문에 결제 레코드가 존재: {unexpected_payment['cnt']}건 (결제가 발생하면 안 됨)")
    else:
        ok("STOCK_FAILED 주문에는 결제 레코드 없음")

    # 집계 요약
    rows = q(cur, """
        SELECT p.status, COUNT(*) AS cnt
        FROM payments p
        GROUP BY p.status
    """)
    total_pay = sum(r["cnt"] for r in rows)
    info(f"총 결제 레코드: {total_pay}")
    for r in rows:
        print(f"    payments.status={r['status']:<12} {r['cnt']:>6} 건")


def check_stock_consistency(cur):
    """5. 재고 정합성 — quantity ≥ 0, reservedQuantity ≥ 0"""
    section("5. 재고 정합성 (stock)")

    # 음수 재고
    neg = q1(cur, "SELECT COUNT(*) AS cnt FROM stock WHERE quantity < 0 OR reserved_quantity < 0")
    if neg and neg["cnt"] > 0:
        fail(f"음수 재고 또는 음수 예약 수량 발견: {neg['cnt']}건")
    else:
        ok("모든 재고/예약 수량이 0 이상")

    # 현재 재고 스냅샷
    rows = q(cur, """
        SELECT
            BIN_TO_UUID(product_id) AS product_id,
            quantity,
            reserved_quantity,
            quantity + reserved_quantity AS total_on_hand,
            version
        FROM stock
        WHERE deleted_at IS NULL
        ORDER BY updated_at DESC
    """)
    info(f"활성 재고 레코드: {len(rows)}개")
    for r in rows:
        print(f"    product={r['product_id'][:8]}…  qty={r['quantity']}  reserved={r['reserved_quantity']}  total={r['total_on_hand']}  version={r['version']}")

    # COMPLETED 주문 수 × quantity 합 vs stock 변화 (간이 검증)
    # reservedQuantity == 0  이어야 함 (모든 결제가 완료된 경우)
    rows2 = q(cur, "SELECT reserved_quantity FROM stock WHERE deleted_at IS NULL AND reserved_quantity > 0")
    if rows2:
        warn(f"아직 reservedQuantity > 0 인 재고 {len(rows2)}개 — 진행 중인 Saga 또는 미완료 Saga 잔여분일 수 있음")
        for r in rows2:
            print(f"    reserved_quantity={r['reserved_quantity']}")
    else:
        ok("모든 재고의 reservedQuantity = 0 (모든 Saga 완결)")


def check_inbox_deduplication(cur):
    """6. Inbox 멱등성 검증 — event_id 중복 없어야 함"""
    section("6. Inbox 멱등성 (stock_inbox_event, payment_inbox_event)")

    for tbl in ("stock_inbox_event", "payment_inbox_event"):
        # 테이블 존재 여부
        exists = q1(cur, f"SHOW TABLES LIKE '{tbl}'")
        if not exists:
            warn(f"테이블 {tbl} 없음 — 서비스가 아직 한 번도 실행 안 된 것일 수 있음")
            continue

        dup = q1(cur, f"""
            SELECT COUNT(*) AS cnt
            FROM (
                SELECT event_id, COUNT(*) AS c
                FROM `{tbl}`
                GROUP BY event_id
                HAVING c > 1
            ) sub
        """)
        if dup and dup["cnt"] > 0:
            fail(f"{tbl}: event_id 중복 {dup['cnt']}건 — Inbox 멱등성 위반!")
        else:
            total = q1(cur, f"SELECT COUNT(*) AS cnt FROM `{tbl}`")
            ok(f"{tbl}: event_id 중복 없음 (총 {total['cnt']}건 수신)")

        # 이벤트 타입 분포
        types = q(cur, f"""
            SELECT event_type, COUNT(*) AS cnt
            FROM `{tbl}`
            GROUP BY event_type
            ORDER BY cnt DESC
        """)
        for t in types:
            print(f"    {tbl}.{t['event_type']:<35} {t['cnt']:>6} 건")


def check_saga_id_uniqueness(cur):
    """7. sagaId 유일성 — orders와 payments 간 1:1 매핑 확인"""
    section("7. sagaId 유일성")

    dup_orders = q1(cur, """
        SELECT COUNT(*) AS cnt FROM (
            SELECT saga_id FROM orders GROUP BY saga_id HAVING COUNT(*) > 1
        ) sub
    """)
    if dup_orders and dup_orders["cnt"] > 0:
        fail(f"orders 테이블에 sagaId 중복: {dup_orders['cnt']}건")
    else:
        ok("orders.sagaId 모두 고유")

    dup_payments = q1(cur, """
        SELECT COUNT(*) AS cnt FROM (
            SELECT saga_id FROM payments GROUP BY saga_id HAVING COUNT(*) > 1
        ) sub
    """)
    if dup_payments and dup_payments["cnt"] > 0:
        fail(f"payments 테이블에 sagaId 중복: {dup_payments['cnt']}건")
    else:
        ok("payments.sagaId 모두 고유")


def check_payment_success_rate(cur):
    """8. 결제 성공률 — Fake PG 90% 성공 기댓값 확인"""
    section("8. 결제 성공률 (Fake PG 기댓값 90%)")
    rows = q(cur, """
        SELECT status, COUNT(*) AS cnt
        FROM payments
        GROUP BY status
    """)
    total = sum(r["cnt"] for r in rows)
    if total == 0:
        info("결제 레코드 없음")
        return
    completed = next((r["cnt"] for r in rows if r["status"] == "COMPLETED"), 0)
    rate = completed / total * 100
    info(f"결제 성공률: {completed}/{total} = {rate:.1f}%  (기댓값 ~90%)")
    if 75 <= rate <= 99:
        ok(f"성공률 {rate:.1f}% — 정상 범위")
    else:
        warn(f"성공률 {rate:.1f}% — 기댓값(90%)에서 크게 벗어남 (샘플 수 적으면 자연스러울 수 있음)")


# ══════════════════════════════════════════════
# 메인
# ══════════════════════════════════════════════

def main():
    print(f"\n{BOLD}{'═'*60}{RESET}")
    print(f"{BOLD}  Saga 정합성 검증 — shop DB{RESET}")
    print(f"{BOLD}{'═'*60}{RESET}")

    try:
        conn = mysql.connector.connect(**DB_CONFIG)
    except mysql.connector.Error as e:
        print(f"\n{RED}DB 접속 실패: {e}{RESET}")
        sys.exit(1)

    cur = conn.cursor()

    check_order_status_distribution(cur)
    check_order_total_amount(cur)
    check_order_item_orphan(cur)
    check_order_payment_consistency(cur)
    check_stock_consistency(cur)
    check_inbox_deduplication(cur)
    check_saga_id_uniqueness(cur)
    check_payment_success_rate(cur)

    cur.close()
    conn.close()

    # ─── 최종 요약 ───────────────────────────────────────────────────
    section("최종 결과")
    if errors:
        print(f"  {RED}{BOLD}정합성 오류 {len(errors)}건{RESET}")
        for e in errors:
            print(f"    ✗ {e}")
    else:
        print(f"  {GREEN}{BOLD}정합성 오류 없음 🎉{RESET}")

    if warnings:
        print(f"  {YELLOW}경고 {len(warnings)}건{RESET}")
        for w in warnings:
            print(f"    △ {w}")

    print()
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
