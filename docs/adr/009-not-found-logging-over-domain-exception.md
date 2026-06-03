# ADR 009: 예외 처리 전략 — Not Found 로깅, 도메인 예외 분리, 원본 상태코드 보존

- **상태**: 수락됨 (Accepted)
- **날짜**: 2026-06-02

---

## 맥락 (Context)

코드 리뷰에서 `GlobalExceptionHandler.handleNotFound()`가 예외를 조용히 삼키고 있다는 지적이 있었다. 현재 핸들러는 아래와 같이 로그 없이 404를 반환한다:

```java
@ExceptionHandler(NoSuchElementException.class)
public ResponseEntity<Void> handleNotFound(NoSuchElementException e) {
    return ResponseEntity.notFound().build();
}
```

이 상황에서 운영 모니터링 시 404가 어떤 도메인·식별자에서 발생했는지 추적할 수단이 없었다. 또한 `orElseThrow()` 호출 9곳에 메시지가 없어 예외 메시지 자체도 비어 있는 상태였다.

개선 방법으로 두 가지가 논의되었다:

1. **도메인별 커스텀 예외 분리** — `ProductNotFoundException`, `CategoryNotFoundException` 등 도메인마다 예외 클래스를 만들고 핸들러를 각각 등록
2. **NoSuchElementException 유지 + 로깅 추가** — 기존 예외 구조를 그대로 두고 메시지와 로그만 추가

이 작업은 기존 동작을 보존하는 리팩터링 스코프였기 때문에 클라이언트 응답을 바꾸지 않는 것이 전제 조건이었다. `handleNotFound`의 반환 타입은 `ResponseEntity<Void>` — 응답 바디가 없어 커스텀 예외를 만들더라도 클라이언트에 추가 정보를 전달할 수 없는 구조였다.

---

## 결정 (Decision)

커스텀 도메인 예외를 만들지 않고, 다음 두 가지만 변경한다:

**1. orElseThrow()에 메시지 통일**

메시지 없는 9곳에 기존 포맷(`"카테고리가 존재하지 않습니다. id=" + id`)과 동일한 패턴으로 메시지를 추가한다. 식별자가 `email`인 경우(`MemberQueryService.findByEmail`)도 메시지에 포함한다.

```java
// Before
categoryRepository.findById(id).orElseThrow();

// After
categoryRepository.findById(id)
    .orElseThrow(() -> new NoSuchElementException("카테고리가 존재하지 않습니다. id=" + id));
```

**2. GlobalExceptionHandler에 warn 로그 추가**

```java
private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

@ExceptionHandler(NoSuchElementException.class)
public ResponseEntity<Void> handleNotFound(NoSuchElementException e) {
    log.warn("Resource not found: {}", e.getMessage());
    return ResponseEntity.notFound().build();  // 응답 변경 없음
}
```

로그 레벨은 `warn` — 404는 서버 오류가 아닌 클라이언트 요청 실패이므로 `error`는 부적절하다. 운영 중 어떤 도메인·식별자에서 발생했는지는 메시지와 HTTP 접근 로그의 조합으로 추적한다.

---

## 고려한 대안 (Alternatives Considered)

| 대안 | 기각 이유 |
|------|----------|
| 도메인별 커스텀 예외 분리 (`ProductNotFoundException` 등) | `handleNotFound`의 반환 타입이 `Void`라 클라이언트는 어차피 메시지를 받지 못함. 도메인 수만큼 예외 클래스와 핸들러가 늘어나는 보일러플레이트 대비 실익이 없음 |
| 응답 바디에 에러 메시지 추가 | 이 작업은 리팩터링 스코프 — 외부 동작(HTTP 상태 코드, 바디)은 변경하지 않는 것이 전제다. 현재 `Void` 바디에 메시지를 추가하면 기존 클라이언트가 빈 바디를 기대하던 동작이 달라질 수 있어 리팩터링이 아닌 동작 변경이 된다. API 계약 변경이 필요하다면 별도 작업으로 논의해야 한다 |

---

## 결과 (Consequences)

**긍정적:**
- 신규 클래스 없이 최소한의 변경으로 운영 모니터링 가능
- 클라이언트 응답(HTTP 상태 코드, 바디)은 완전히 동일하게 유지 — 기존 클라이언트 동작에 영향 없음
- `NoSuchElementException`을 일관되게 사용하는 기존 설계(`findById().orElseThrow()`)를 유지

**부정적:**
- 도메인별 예외 클래스가 없으므로 나중에 도메인마다 다른 HTTP 상태 코드가 필요해지면 핸들러 분기가 복잡해질 수 있음
- 로그에 email이 포함되므로 로그 접근 권한 관리가 필요

---

## 후속 결정: 비즈니스 규칙 위반 예외 분리 및 원본 상태코드 보존

Not Found 이외의 `IllegalArgumentException` 사용처를 분석한 결과, 비즈니스 규칙 위반 3곳을 도메인 특화 예외로 분리하기로 결정했다. 이 과정에서 초기 커밋(613bea3) 기준 원본 상태코드를 분석하고, 그 동작을 보존하는 방향으로 `GlobalExceptionHandler`를 설정했다.

### 원본 컨트롤러별 상태코드 분석

초기 코드에서 컨트롤러마다 `@ExceptionHandler` 보유 여부가 달랐다:

| 시나리오 | 원래 컨트롤러 | 원본 상태코드 |
|---------|------------|------------|
| 재고 부족 (`subtractQuantity`) | `OrderController` — 핸들러 없음 | **500** |
| 포인트 부족 (`deductPoint`) | `OrderController` — 핸들러 없음 | **500** |
| 최소 옵션 규칙 (`removeOption`) | `OptionController` — 컨트롤러 내 `@ExceptionHandler` 보유 | **400** + 메시지 |

### 후속 결정 내용

`IllegalArgumentException`으로 통합되어 있던 비즈니스 규칙 위반 3곳을 도메인 패키지 내 전용 예외 클래스로 분리한다:

- `gift.option.InsufficientStockException` → `GlobalExceptionHandler`에서 **500** 반환 (OrderController 원본과 일치)
- `gift.member.point.InsufficientPointException` → `GlobalExceptionHandler`에서 **500** 반환 (OrderController 원본과 일치)
- `gift.product.MinimumOptionException` → `GlobalExceptionHandler`에서 **400** + 메시지 반환 (OptionController 원본과 일치)

### 후속 결정에서 고려한 대안

| 대안 | 기각 이유 |
|------|----------|
| 재고·포인트 예외도 400 반환 | 리팩터링 스코프이므로 원본 동작(500)을 변경하면 안 됨. 400으로 개선하는 것은 별도 논의가 필요한 동작 변경임 |
| 핸들러 없이 예외 전파 | `@AutoConfigureMockMvc` 환경에서 미처리 예외는 MockMvc가 500으로 변환하지 않고 테스트로 전파되어 컨트롤러 테스트 작성이 불가능해짐. 명시적 핸들러로 500을 반환해야 테스트 검증이 가능함 |
