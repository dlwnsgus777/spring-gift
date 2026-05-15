# spec: 스타일 정리 (2단계)

## 1. 기능 개요

기능 변경 없이 코드 가독성을 높인다.
의미 없는 흐름 주석, `null` 반환 패턴, `var` 혼용을 제거해 읽기 쉬운 코드로 정리한다.
모든 변경은 순수 삭제·교체이며 동작이 달라지지 않는다. 통합 테스트가 그대로 Green이면 성공이다.

### 기능 구성

| 작업 항목 | 대상 파일 |
|----------|-----------|
| **흐름 주석 제거** | `OrderController`, `WishController` |
| **미사용 import 확인** | 전체 Controller — 실제 없음, 확인만 |
| **Optional 패턴 통일** | `ProductController`, `OrderController`, `WishController` |
| **`var` 타입 명시 통일** | `OrderController`, `WishController` |

---

## 2. 변경 대상 분석

### 2-1. 의미 없는 흐름 주석

코드가 이미 명확히 표현하는 내용을 반복하는 주석을 제거한다.
단, **"why"를 설명하는 주석(동작 특성·의도)**은 유지한다.

#### OrderController.java

| 위치 | 주석 내용 | 처리 |
|------|-----------|------|
| L52 | `// auth check` | 제거 |
| L61–68 | `// order flow: 1. auth check …` 블록 전체 | 제거 |
| L74 | `// auth check` | 제거 |
| L80 | `// validate option` | 제거 |
| L86 | `// subtract stock` | 제거 |
| L90 | `// deduct points` | 제거 |
| L95 | `// save order` | 제거 |
| L98 | `// best-effort kakao notification` | **유지** — 실패해도 주문은 완료된다는 동작 특성을 전달 |

#### WishController.java

| 위치 | 주석 내용 | 처리 |
|------|-----------|------|
| L42 | `// check auth` | 제거 |
| L56 | `// check auth` | 제거 |
| L62 | `// check product` | 제거 |
| L68 | `// check duplicate` | 제거 |
| L84 | `// check auth` | 제거 |

---

### 2-2. 미사용 import 확인

전체 Controller를 확인한 결과 미사용 import 없음.
`ProductController`의 `import java.util.List` — `validateName()` 내 `List<String>` 로 사용.
`WishController`의 `import org.springframework.web.bind.annotation.PathVariable` — `removeWish(@PathVariable)` 로 사용.
**변경 없음**, 체크리스트 항목 완료로 처리.

---

### 2-3. null 반환 대신 Optional 패턴 통일

현재: `.orElse(null)` 후 null 체크 → 이미 `Optional<T>`를 반환하는 Repository를 다시 null로 풀어낸다.
목표: Optional 체인 `.map(…).orElse(…)` 으로 null 경로를 타입 수준에서 제거한다.

**변경 전 패턴:**
```java
Product product = productRepository.findById(id).orElse(null);
if (product == null) {
    return ResponseEntity.notFound().build();
}
return ResponseEntity.ok(ProductResponse.from(product));
```

**변경 후 패턴:**
```java
return productRepository.findById(id)
    .map(product -> ResponseEntity.ok(ProductResponse.from(product)))
    .orElse(ResponseEntity.notFound().build());
```

#### 변경 대상 목록

**ProductController.java**

| 메서드 | 변경 전 | 변경 후 |
|--------|---------|---------|
| `getProduct` (L40–46) | `findById().orElse(null)` + null 체크 | Optional 체인으로 교체 |
| `createProduct` (L52–55) | `findById().orElse(null)` + null 체크 | Optional 체인으로 교체 |
| `updateProduct` — category (L69–72) | `findById().orElse(null)` + null 체크 | Optional 체인으로 교체 |
| `updateProduct` — product (L74–77) | `findById().orElse(null)` + null 체크 | Optional 체인으로 교체 |

> `updateProduct`는 category와 product 두 조회가 중첩되어 체인이 복잡해진다.
> 이 경우 `orElseThrow`로 예외를 던지고 기존 `@ExceptionHandler(IllegalArgumentException.class)`를 재활용하거나,
> 별도 `@ExceptionHandler(NoSuchElementException.class)`를 추가한다.
> 어떤 방식이든 **응답 스펙(404)은 동일**해야 한다.

**OrderController.java**

| 메서드 | 변경 전 | 변경 후 |
|--------|---------|---------|
| `createOrder` — option 조회 (L81–84) | `findById().orElse(null)` + null 체크 | Optional 체인으로 교체 |

**WishController.java**

| 메서드 | 변경 전 | 변경 후 |
|--------|---------|---------|
| `addWish` — product 조회 (L63–66) | `findById().orElse(null)` + null 체크 | Optional 체인으로 교체 |
| `addWish` — 중복 확인 (L69–72) | `findByMemberIdAndProductId().orElse(null)` + null 체크 | Optional 체인으로 교체 |
| `removeWish` — wish 조회 (L90–93) | `findById().orElse(null)` + null 체크 | Optional 체인으로 교체 |

---

### 2-4. `var` 혼용 정리

`ProductController`·`AdminProductController`는 타입을 명시한다. `OrderController`·`WishController`는 `var`를 사용해 프로젝트 내 일관성이 없다.
`var` → 타입 명시로 통일한다.

**OrderController.java 변경 대상**

| 라인 | 현재 | 변경 후 |
|------|------|---------|
| L53 | `var member` | `Member member` |
| L57 | `var orders` | `Page<OrderResponse> orders` |
| L75 | `var member` | `Member member` |
| L81 | `var option` | `Option option` |
| L91 | `var price` | `long price` (또는 `int price`) |
| L96 | `var saved` | `Order saved` |
| L109 | `var product` | `Product product` |

**WishController.java 변경 대상**

| 라인 | 현재 | 변경 후 |
|------|------|---------|
| L43 | `var member` | `Member member` |
| L47 | `var wishes` | `Page<WishResponse> wishes` |
| L57 | `var member` | `Member member` |
| L63 | `var product` | `Product product` |
| L69 | `var existing` | `Optional<Wish> existing` (체인 전환 시 제거) |
| L74 | `var saved` | `Wish saved` |
| L85 | `var member` | `Member member` |
| L90 | `var wish` | `Wish wish` |

---

## 3. 구현 대상 파일

```
src/main/java/gift/
├── order/OrderController.java      (흐름 주석 제거 + Optional 패턴 + var 타입 명시)
├── product/ProductController.java  (Optional 패턴 통일)
└── wish/WishController.java        (흐름 주석 제거 + Optional 패턴 + var 타입 명시)
```

---

## 4. 주요 고려사항

1. **동작 불변 원칙**: 모든 변경 후 기존 통합 테스트(`FlywayMigrationTest`, 추후 추가될 API 테스트)가 Green을 유지해야 한다.

2. **`updateProduct` 중첩 조회**: category → product 순서로 두 번 조회한다. Optional 체인 중첩은 가독성을 해친다. `orElseThrow()`로 예외를 던지고 `@ExceptionHandler`에서 404로 매핑하는 패턴이 더 읽기 쉽다.
   - `NoSuchElementException` → 404 핸들러 추가 필요 (기존 `IllegalArgumentException` 핸들러는 400 반환)

3. **`best-effort kakao notification` 주석 유지**: 이 주석은 "카카오 메시지 발송이 실패해도 주문은 완료된다"는 동작 특성을 전달한다. 제거하면 `try-catch + ignored`의 의도를 알 수 없게 된다.

4. **price 타입**: `option.getProduct().getPrice() * request.quantity()`의 결과 타입을 확인하고 `long` 또는 `int`로 명시한다.

---

## 5. 구현 순서 (체크리스트)

1. [ ] `OrderController.java` — 흐름 주석 제거 (8곳 → `best-effort` 제외 7곳)
2. [ ] `WishController.java` — 흐름 주석 제거 (5곳)
3. [ ] `ProductController.java` — Optional 패턴 통일 (4곳)
4. [ ] `OrderController.java` — Optional 패턴 통일 + `var` 타입 명시
5. [ ] `WishController.java` — Optional 패턴 통일 + `var` 타입 명시
6. [ ] 통합 테스트 실행 (`./gradlew test`) — Green 확인

---

## 6. 인수 조건 (Acceptance Criteria)

- [ ] `OrderController` 내 흐름 주석이 모두 제거된다 (`best-effort kakao notification` 제외)
- [ ] `WishController` 내 `// check auth`, `// check product`, `// check duplicate` 주석이 모두 제거된다
- [ ] Controller 내 `.orElse(null)` + null 체크 패턴이 Optional 체인으로 교체된다
- [ ] `OrderController`, `WishController` 내 `var`가 타입 명시로 교체된다
- [ ] `./gradlew test` 실행 시 모든 테스트가 Green을 유지한다
- [ ] diff에 로직 추가는 없고 삭제·교체만 존재한다
