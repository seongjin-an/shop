이번 세션에서 해결한 것들 정리해드리면:

**버그 수정**
- `CompensateStockUseCase` — try-catch 제거로 예외 전파 복구
- `StockKafkaConsumer` — `finally { ack }` → 성공 시에만 ack로 메시지 유실 방지

**아키텍처 개선**
- `DefaultErrorHandler` → `DeadLetterPublishingRecoverer`로 교체 (메시지 보존)
- `StockDltConsumer` + `DltRetryService` 추가 (3초~30초 간격 10회 재시도)
- 재시도 레이어 4단계 완성: `@Retryable × 5` → `DefaultErrorHandler × 3` → `DLT × 10` → 수동 개입

**운영 도구**
- Loki + Promtail + Grafana 로그 수집 스택
- `start-services.sh` / `stop-services.sh`
- `check_consistency.py` 정합성 검증 스크립트

sagaId 파티셔닝 트레이드오프 분석, 낙관/비관락 비교, 스케일 아웃 한계까지 실제로 부딪히면서 확인하셨으니 이 패턴들은 확실히 몸에 익으셨을 겁니다. 다음 작업 있으면 편하게 말씀해주세요!