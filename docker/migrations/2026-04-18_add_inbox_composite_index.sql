-- ============================================================
-- Manual fallback DDL
-- 목적: stock_inbox_event 에 (saga_id, event_type) 복합 인덱스 추가
-- 근거: CompensateStockUseCase.findBySagaIdAndEventType 가 full scan 되어
--       단일 span 기준 29ms 병목으로 측정됨 (Tempo trace 참조)
--
-- 적용 시점:
--   1) shop-stock 재기동 후 SHOW INDEX 에 idx_inbox_saga_event_type 이 보이면 스킵.
--   2) Hibernate ddl-auto=update 가 인덱스 추가를 놓친 경우에만 수동 실행.
--
-- 실행:
--   cd docker
--   docker compose exec -T mysql \
--     mysql -udev_user -pdev_password shop \
--     < migrations/2026-04-18_add_inbox_composite_index.sql
-- ============================================================

USE shop;

-- 이미 있으면 노옵. MySQL 8.0+ 은 IF NOT EXISTS 를 CREATE INDEX 에서 지원하지 않으므로
-- information_schema 로 존재 여부 확인 후 조건부 실행.
SET @idx_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name   = 'stock_inbox_event'
      AND index_name   = 'idx_inbox_saga_event_type'
);

SET @ddl := IF(
    @idx_exists = 0,
    'CREATE INDEX idx_inbox_saga_event_type ON stock_inbox_event (saga_id, event_type)',
    'SELECT ''[skip] idx_inbox_saga_event_type already exists'' AS note'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 확인
SHOW INDEX FROM stock_inbox_event WHERE Key_name = 'idx_inbox_saga_event_type';
