# ADR 008: 재고 동시성 처리에 Pessimistic Lock 적용

- **상태**: 수락됨 (Accepted)
- **날짜**: 2026-05-14

---

## 맥락 (Context)

`OrderCommandService.createOrder()`는 다음 순서로 옵션 재고를 차감한다.

```
optionRepository.findById(optionId)   // 재고 읽기
option.subtractQuantity(quantity)      // 메모리에서 차감
optionRepository.save(option)          // DB에 저장
```

두 요청이 동시에 같은 `options` 행을 읽으면 둘 다 동일한 재고를 기준으로 차감해 저장한다. 예를 들어 재고가 3개인 옵션에 수량 2짜리 주문 두 건이 동시에 들어오면, 둘 다 재고 3을 읽고 각자 1을 저장해 재고가 -1이 되어야 할 상황에서 1이 남아 있는 것처럼 처리된다.

`Option.subtractQuantity()`는 재고 초과 시 `IllegalArgumentException`을 던지지만, 이 검증은 DB에서 읽은 시점의 값을 기준으로 하므로 동시 읽기 상황에서는 두 요청 모두 검증을 통과한다.

선물하기 서비스 특성상 인기 상품은 한정 재고를 여러 사용자가 동시에 주문하는 경합(high contention) 시나리오가 발생할 수 있다.

---

## 결정 (Decision)

`OptionRepository`에 `@Lock(LockModeType.PESSIMISTIC_WRITE)` 메서드를 추가하고, `OrderCommandService`에서 재고 조회 시 이 메서드를 사용한다.

```java
// OptionRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT o FROM Option o WHERE o.id = :id")
Optional<Option> findByIdWithLock(@Param("id") Long id);
```

```java
// OrderCommandService.createOrder()
Option option = optionRepository.findByIdWithLock(optionId).orElseThrow(); // SELECT FOR UPDATE
```

`SELECT FOR UPDATE`는 해당 행에 배타적 잠금을 걸어 첫 번째 트랜잭션이 커밋될 때까지 두 번째 요청이 대기하도록 강제한다. 첫 번째 트랜잭션이 커밋되면 두 번째 요청은 갱신된 재고를 읽어 정확하게 처리된다.

---

## 고려한 대안 (Alternatives Considered)

| 대안 | 기각 이유 |
|------|----------|
| **Optimistic Lock (`@Version`)** | `options` 테이블에 `version BIGINT` 컬럼 추가와 Flyway 마이그레이션이 필요하다. 또한 경합이 잦은 환경에서 두 번째 요청이 `OptimisticLockException`으로 계속 실패하므로 클라이언트 재시도 로직이 추가로 필요하다. |
| **애플리케이션 레벨 락 (synchronized / ReentrantLock)** | JVM 단위 락이라 다중 인스턴스 배포 시 무효화된다. |

---

## 결과 (Consequences)

**긍정적:**
- 스키마 변경 없이 동시성 문제를 해결한다 — `options` 테이블에 컬럼 추가나 Flyway 마이그레이션이 불필요하다
- 두 번째 요청이 실패하지 않고 대기 후 정확한 재고로 처리된다 — 클라이언트에 재시도 로직이 필요 없다
- 잠금 범위가 `options` 단일 행이라 데드락 위험이 낮다
- `OptionRepository.findByIdWithLock()` 하나만 추가하면 되어 변경 범위가 작다

**부정적:**
- 동시 요청이 많을수록 대기 시간이 늘어나 처리량(throughput)이 떨어진다 — 재고가 충분해 경합이 낮은 상품도 잠금 비용을 부담한다
- 트랜잭션이 길어질수록 다른 요청의 대기 시간이 증가하므로, `createOrder()` 트랜잭션 범위를 최소화해야 한다 (카카오 알림을 트랜잭션 외부로 분리한 ADR 이유와 연결됨)
