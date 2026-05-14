# spec: Order 도메인 8단계 — 서비스 추출 + 트랜잭션 + 위시 자동 삭제

## 1. 기능 개요

Order 도메인은 재고 차감, 포인트 결제, 위시리스트 자동 삭제, 카카오 알림 발송을 단일 흐름 안에서 조율한다. 현재 OrderController가 5개 Repository를 직접 의존하고 `@Transactional`이 없어 재고 차감 후 포인트 부족 시 재고가 롤백되지 않는 버그가 존재한다. 이번 단계에서는 서비스 계층을 추출하고, 트랜잭션 경계를 확립하고, 위시리스트 자동 삭제 누락 기능을 구현한다.

### 작업 구성

| 작업 | 설명 |
|------|------|
| **통합 테스트 작성** | 기존 동작을 Green 테스트로 고정 |
| **스타일 정리** | 흐름 주석 제거, `var` → 타입 명시, `AuthService` 전환 |
| **서비스 추출** | `OrderQueryService` + `OrderCommandService` 분리 |
| **트랜잭션 경계 확립** | `@Transactional` 적용 + 롤백 시나리오 테스트 |
| **위시 자동 삭제** | 주문 완료 시 위시리스트에 있는 상품 자동 삭제 |
| **재고 동시성 처리** | Pessimistic Lock — `SELECT FOR UPDATE`로 동시 주문 직렬화 |

---

## 2. API 설계

### API 1 — 주문 생성

```
POST /api/orders
Authorization: Bearer {token}
Content-Type: application/json
```

- 옵션 재고 차감 → 포인트 결제 → 주문 저장 → 위시 자동 삭제 → 카카오 알림(best-effort) 순으로 처리한다

**Request**
```json
{
  "optionId": 1,
  "quantity": 2,
  "message": "생일 축하해!"
}
```

**Response 201 Created**
```json
{
  "id": 5,
  "optionId": 1,
  "quantity": 2,
  "orderDateTime": "2026-05-14T10:30:00",
  "message": "생일 축하해!"
}
```

**엣지 케이스**

| 조건 | 응답 |
|------|------|
| Authorization 헤더 없음 | 400 (Spring missing required header) |
| 유효하지 않은 JWT | 401 |
| 존재하지 않는 optionId | 404 |
| 요청 수량 > 재고 | 400 |
| 포인트 부족 | 400 |
| message 없음 | 201, message: null |

---

### API 2 — 주문 목록 조회

```
GET /api/orders?page=0&size=10
Authorization: Bearer {token}
```

- 인증된 회원 본인의 주문 목록을 페이징으로 반환한다

**Response 200 OK**
```json
{
  "content": [
    {
      "id": 1,
      "optionId": 3,
      "quantity": 1,
      "orderDateTime": "2026-02-10T14:30:00",
      "message": "생일 축하해! 🎉"
    }
  ],
  "totalElements": 2,
  "totalPages": 1
}
```

---

## 3. 비즈니스 로직

### 3-1. 주문 생성 흐름

1. `authService.extractMember(authorization)` — 유효하지 않은 토큰이면 `UnauthorizedException` → 401
2. `optionRepository.findByIdWithLock(optionId).orElseThrow()` — **Pessimistic Lock** (`SELECT FOR UPDATE`), 존재하지 않으면 `NoSuchElementException` → 404
3. `option.subtractQuantity(quantity)` — 재고 초과면 `IllegalArgumentException` → 400
4. `memberRepository.findById(memberId).orElseThrow()` — member 로드
5. `member.deductPoint(price)` — `price = option.getProduct().getPrice() × quantity`, 포인트 부족이면 `IllegalArgumentException` → 400
6. `orderRepository.save(new Order(...))` — 주문 저장
7. `wishCommandService.deleteByMemberIdAndProductId(memberId, productId)` — 위시에 있으면 삭제, 없으면 무시
8. `orderCommandService.notifyKakaoIfPossible(member, order)` — 트랜잭션 커밋 후 컨트롤러에서 호출, Kakao Access Token이 없거나 발송 실패 시 무시 (best-effort)

> **트랜잭션 범위**: 2~7번이 단일 트랜잭션이다. 8번(카카오 알림)은 트랜잭션 외부에서 best-effort로 실행된다.

### 3-2. 트랜잭션 경계 — 버그와 수정

**현재 (버그)**:
- `@Transactional` 없음 → 재고 차감 후 포인트 부족 예외 발생 시 재고가 차감된 채로 남는다

**수정 후**:
- `OrderCommandService.createOrder()`에 `@Transactional` 적용 → 포인트 부족 예외 발생 시 재고도 롤백된다

### 3-3. 위시리스트 자동 삭제

주문한 상품이 위시리스트에 없어도 오류 없이 처리한다. `WishCommandService.deleteByMemberIdAndProductId(memberId, productId)`가 위시를 찾지 못하면 아무것도 하지 않는다.

---

## 4. 구현 대상 파일

### order 모듈

| 파일 | 역할 | 변경 |
|------|------|------|
| `OrderController` | HTTP 계층 — AuthService + QueryService + CommandService 위임 | 수정 |
| `OrderQueryService` | 주문 조회 (`@Transactional(readOnly = true)`) | 신규 |
| `OrderCommandService` | 주문 생성, 재고/포인트 차감, 위시 삭제, 카카오 알림(트랜잭션 외부) (`@Transactional`) | 신규 |
| `OrderControllerTest` | API 통합 테스트 (MockMvc) | 신규 |
| `OrderQueryServiceTest` | 조회 서비스 단위 테스트 | 신규 |
| `OrderCommandServiceTest` | 생성 서비스 단위 테스트 (트랜잭션 롤백 포함) | 신규 |

### wish 모듈

| 파일 | 역할 | 변경 |
|------|------|------|
| `WishCommandService` | `deleteByMemberIdAndProductId(memberId, productId)` 추가 | 수정 |
| `WishCommandServiceTest` | 위 메서드 테스트 추가 | 수정 |

### option 모듈

| 파일 | 역할 | 변경 |
|------|------|------|
| `OptionRepository` | `findByIdWithLock()` 추가 — `@Lock(PESSIMISTIC_WRITE)` | 수정 |

### 패키지 위치

```
src/main/java/gift/order/
├── Order.java                   (변경 없음)
├── OrderController.java         (수정)
├── OrderQueryService.java       (신규)
├── OrderCommandService.java     (신규)
├── OrderRepository.java         (변경 없음)
├── OrderRequest.java            (변경 없음)
├── OrderResponse.java           (변경 없음)
└── KakaoMessageClient.java      (변경 없음)

src/main/java/gift/option/
└── OptionRepository.java        (수정 — findByIdWithLock 추가)

src/main/java/gift/wish/
└── WishCommandService.java      (수정 — 메서드 추가)

src/test/java/gift/order/
├── OrderControllerTest.java     (신규)
├── OrderQueryServiceTest.java   (신규)
└── OrderCommandServiceTest.java (신규)

src/test/java/gift/wish/
└── WishCommandServiceTest.java  (수정 — 테스트 추가)
```

### 코드 스니핏

**OrderController.java** (수정 후)

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final AuthService authService;
    private final OrderQueryService orderQueryService;
    private final OrderCommandService orderCommandService;

    public OrderController(
        AuthService authService,
        OrderQueryService orderQueryService,
        OrderCommandService orderCommandService
    ) { ... }

    @GetMapping
    public ResponseEntity<Page<OrderResponse>> getOrders(
        @RequestHeader("Authorization") String authorization,
        Pageable pageable
    ) {
        Member member = authService.extractMember(authorization);
        Page<OrderResponse> orders = orderQueryService.findByMemberId(member.getId(), pageable)
            .map(OrderResponse::from);
        return ResponseEntity.ok(orders);
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody OrderRequest request
    ) {
        Member member = authService.extractMember(authorization);
        Order order = orderCommandService.createOrder(
            member.getId(), request.optionId(), request.quantity(), request.message()
        );
        // 트랜잭션 커밋 후 카카오 알림 (best-effort, 트랜잭션 범위 외부)
        orderCommandService.notifyKakaoIfPossible(member, order);
        return ResponseEntity.created(URI.create("/api/orders/" + order.getId()))
            .body(OrderResponse.from(order));
    }
}
```

**OptionRepository.java** (수정 — findByIdWithLock 추가)

```java
public interface OptionRepository extends JpaRepository<Option, Long> {
    List<Option> findByProductId(Long productId);
    boolean existsByProductIdAndName(Long productId, String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Option o WHERE o.id = :id")
    Optional<Option> findByIdWithLock(@Param("id") Long id);
}
```

**OrderQueryService.java** (신규)

```java
@Service
@Transactional(readOnly = true)
public class OrderQueryService {
    private final OrderRepository orderRepository;

    public OrderQueryService(OrderRepository orderRepository) { ... }

    public Page<Order> findByMemberId(Long memberId, Pageable pageable) {
        return orderRepository.findByMemberId(memberId, pageable);
    }
}
```

**OrderCommandService.java** (신규)

```java
@Service
@Transactional
public class OrderCommandService {
    private final OrderRepository orderRepository;
    private final OptionRepository optionRepository;
    private final MemberRepository memberRepository;
    private final WishCommandService wishCommandService;
    private final KakaoMessageClient kakaoMessageClient;

    // 트랜잭션 범위: 재고 차감 + 포인트 차감 + 주문 저장 + 위시 삭제
    // 카카오 알림은 컨트롤러에서 트랜잭션 커밋 후 별도 호출
    public Order createOrder(Long memberId, Long optionId, int quantity, String message) {
        Option option = optionRepository.findByIdWithLock(optionId).orElseThrow(); // SELECT FOR UPDATE
        option.subtractQuantity(quantity);                               // 재고 차감
        optionRepository.save(option);

        Member member = memberRepository.findById(memberId).orElseThrow();
        int price = option.getProduct().getPrice() * quantity;
        member.deductPoint(price);                                       // 포인트 차감
        memberRepository.save(member);

        Order order = orderRepository.save(
            new Order(option, memberId, quantity, message)
        );

        wishCommandService.deleteByMemberIdAndProductId(                 // 위시 자동 삭제
            memberId, option.getProduct().getId()
        );

        return order;
    }

    // @Transactional 없음 — 트랜잭션 커밋 후 컨트롤러에서 호출
    public void notifyKakaoIfPossible(Member member, Order order) {
        if (member.getKakaoAccessToken() == null) return;
        try {
            Option option = order.getOption();
            kakaoMessageClient.sendToMe(member.getKakaoAccessToken(), order, option.getProduct());
        } catch (Exception ignored) { }
    }
}
```

**WishCommandService.java** (추가 메서드)

```java
// 주문 완료 시 해당 상품을 위시리스트에서 자동 삭제
// 위시에 없으면 아무것도 하지 않는다
public void deleteByMemberIdAndProductId(Long memberId, Long productId) {
    wishRepository.findByMemberIdAndProductId(memberId, productId)
        .ifPresent(wishRepository::delete);
}
```

**OrderControllerTest.java** (신규 — 패턴 참고: WishControllerTest)

```java
class OrderControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private OptionRepository optionRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private MemberRepository memberRepository;

    @Test
    @DisplayName("포인트와 재고가 충분하면 주문이 생성되고 201을 반환한다")
    void test01() throws Exception { ... }

    @Test
    @DisplayName("Authorization 헤더 없이 주문하면 400을 반환한다")
    void test02() throws Exception { ... }

    @Test
    @DisplayName("유효하지 않은 토큰으로 주문하면 401을 반환한다")
    void test03() throws Exception { ... }

    @Test
    @DisplayName("존재하지 않는 optionId로 주문하면 404를 반환한다")
    void test04() throws Exception { ... }

    @Test
    @DisplayName("재고보다 많은 수량으로 주문하면 400을 반환한다")
    void test05() throws Exception { ... }

    @Test
    @DisplayName("포인트가 부족하면 400을 반환한다")
    void test06() throws Exception { ... }

    @Test
    @DisplayName("인증된 회원의 주문 목록을 조회하면 200을 반환한다")
    void test07() throws Exception { ... }

    @Test
    @DisplayName("주문 완료 후 위시리스트에 있던 상품이 자동으로 삭제된다")
    void test08() throws Exception { ... }  // 위시 자동 삭제 구현 후 추가
}
```

**OrderCommandServiceTest.java** (신규)

```java
@Transactional
class OrderCommandServiceTest extends AbstractIntegrationTest {

    @Autowired private OrderCommandService orderCommandService;
    @Autowired private OptionRepository optionRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private WishRepository wishRepository;
    // CategoryRepository, ProductRepository ...

    @Test
    @DisplayName("포인트와 재고가 충분하면 주문이 생성되고 재고·포인트가 차감된다")
    void test01() { ... }

    @Test
    @DisplayName("재고보다 많은 수량으로 주문하면 IllegalArgumentException이 발생한다")
    void test02() { ... }

    @Test
    @DisplayName("포인트가 부족하면 IllegalArgumentException이 발생한다")
    void test03() { ... }

    @Test
    @DisplayName("포인트 부족 예외 발생 시 재고가 롤백된다")
    void test04() { ... }  // @Transactional 적용 후 Green

    @Test
    @DisplayName("주문 완료 후 위시리스트에 있던 상품이 삭제된다")
    void test05() { ... }  // 위시 자동 삭제 구현 후 Green
}
```

---

## 5. 주요 고려사항

1. **카카오 알림을 트랜잭션 외부에서 실행**: `createOrder()`가 `@Transactional`이면 카카오 HTTP 호출이 완료될 때까지 DB 커넥션이 잡혀 있다. 네트워크 지연이 긴 경우 커넥션 풀이 고갈될 수 있다. 두 가지 방법 중 하나를 선택한다.
   - **방법 A (권장)**: `createOrder()`는 주문 저장까지만 처리하고 Order를 반환한다. 컨트롤러에서 트랜잭션 커밋 후 `orderCommandService.notifyKakaoIfPossible(member, order)`를 별도 호출한다.
   - **방법 B**: `@TransactionalEventListener(phase = AFTER_COMMIT)`로 알림 이벤트를 발행한다 — 구현 복잡도가 높다.

2. **재고 동시성 — Pessimistic Lock 적용**: 두 요청이 동시에 같은 옵션을 조회하면 둘 다 재고를 읽은 후 각자 차감해서 저장하는 Race Condition이 발생한다. `OptionRepository`에 `@Lock(PESSIMISTIC_WRITE)` 메서드를 추가해 `SELECT FOR UPDATE`로 직렬화한다. 두 번째 요청은 첫 번째 트랜잭션이 커밋될 때까지 대기한 후 정확한 재고로 처리된다.
   - Optimistic Lock 대신 Pessimistic Lock을 선택한 이유: 인기 상품 동시 주문처럼 경합이 잦은 시나리오에서 Optimistic은 두 번째 요청이 계속 실패해 재시도 로직이 필요하다. 또한 `@Version` 컬럼 추가로 스키마 변경(Flyway 마이그레이션)이 수반된다. 주문 흐름은 `options` 단일 행만 잠그므로 데드락 위험도 낮다.

3. **WishCommandService 순환 의존 위험 없음**: `OrderCommandService` → `WishCommandService` 방향은 단방향이다. `WishCommandService`는 Order 패키지를 참조하지 않는다.

4. **test04 (트랜잭션 롤백 테스트) 격리**: 이 테스트는 `@Transactional` 없이 실행해야 "롤백되지 않는 버그"를 증명할 수 있다. `@Transactional`을 붙이면 테스트 자체가 롤백되어 검증이 불가능하다. 실제 DB 상태를 확인하므로 `@Transactional` 없이 작성하고, 데이터는 UUID 기반으로 격리한다.

---

## 6. 구현 순서 (TDD)

> **원칙**: 구조적 변경(동작 변화 없음)을 먼저, 트랜잭션·새 동작을 나중에.

### 1단계 — 통합 테스트 작성 (기존 동작 Green 고정)

- [ ] `OrderControllerTest` 작성 — test01~test07 (위시 자동 삭제 test08 제외)
- [ ] 전체 테스트 Green 확인

### 2단계 — 스타일 정리 (구조 변경 없음, 동작 동일)

- [ ] `OrderController` 흐름 주석 7곳 제거 (`// auth check`, `// validate option` 등)
- [ ] `var` → 명시적 타입 변환
- [ ] `authenticationResolver.extractMember()` → `authService.extractMember()` 전환, null 체크 분기 제거
- [ ] `optionRepository.findById(...).orElse(null)` → `.orElseThrow()` 변환, null 체크 분기 제거
- [ ] 테스트 Green 확인 (응답 코드 동일)

### 3단계 — OrderQueryService 추출 (구조 변경)

- [ ] `OrderQueryService` 생성 — `findByMemberId(memberId, pageable)` 구현
- [ ] `OrderQueryServiceTest` 작성 — test01
- [ ] `OrderController.getOrders()` → `orderQueryService.findByMemberId()` 위임
- [ ] 컨트롤러 테스트 여전히 Green 확인

### 4단계 — OrderCommandService 추출 (구조 변경)

- [ ] `OrderCommandService` 생성 — `createOrder()` 구현 (카카오 알림 포함, `@Transactional` 아직 없음)
- [ ] `OrderCommandServiceTest` 작성 — test01, test02, test03
- [ ] `OrderController.createOrder()` → `orderCommandService.createOrder()` 위임
- [ ] `OrderController`에서 불필요한 Repository 의존성 5개 → `AuthService`, `OrderQueryService`, `OrderCommandService` 3개로 축소
- [ ] 컨트롤러 테스트 여전히 Green 확인

### 5단계 — 트랜잭션 경계 확립 (동작 변경 — 버그 수정)

- [ ] `OrderCommandServiceTest.test04` 작성 — "포인트 부족 시 재고가 롤백된다" → **Red** 확인
- [ ] `OrderCommandService.createOrder()`에 `@Transactional` 적용
- [ ] test04 **Green** 확인

### 6단계 — 위시리스트 자동 삭제 (새 동작 추가)

- [ ] `OrderCommandServiceTest.test05` 작성 — "주문 후 위시에 있던 상품이 삭제된다" → **Red** 확인
- [ ] `WishCommandServiceTest`에 `deleteByMemberIdAndProductId` 테스트 추가 (있으면 삭제, 없으면 무시)
- [ ] `WishCommandService.deleteByMemberIdAndProductId()` 구현 → WishCommandServiceTest **Green**
- [ ] `OrderCommandService.createOrder()`에서 `wishCommandService.deleteByMemberIdAndProductId()` 호출
- [ ] test05 **Green** 확인
- [ ] `OrderControllerTest.test08` 작성 후 **Green** 확인

### 7단계 — 재고 동시성 처리 (Pessimistic Lock)

- [ ] `OptionRepository`에 `findByIdWithLock()` 추가 (`@Lock(PESSIMISTIC_WRITE)` + `@Query`)
- [ ] `OrderCommandService.createOrder()`에서 `findById` → `findByIdWithLock` 교체
- [ ] `OrderCommandServiceTest`에 동시성 테스트 추가 — 두 스레드가 동시에 같은 옵션을 주문할 때 재고가 정확히 차감됨을 검증
- [ ] 전체 테스트 Green 확인

---

## 7. 인수 조건 (Acceptance Criteria)

- [ ] `OrderController`에 Repository 직접 의존이 없다 — `AuthService`, `OrderQueryService`, `OrderCommandService`만 주입
- [ ] `OrderController`에 흐름 주석(`// auth check` 등)이 없다
- [ ] `OrderController`에 null 체크 분기가 없다
- [ ] `OrderCommandService`에 `@Transactional`이 적용되어 있다
- [ ] 재고 차감 후 포인트 부족 예외 발생 시 재고가 롤백됨을 테스트가 증명한다
- [ ] 주문 완료 후 위시리스트에 있던 상품이 자동으로 삭제됨을 테스트가 증명한다
- [ ] 주문한 상품이 위시에 없어도 주문이 정상 완료된다
- [ ] 카카오 알림 발송 실패 시 주문 결과에 영향이 없다
- [ ] 카카오 알림은 트랜잭션 커밋 후 실행된다 (`createOrder()` 트랜잭션 범위 외부)
- [ ] 동시에 같은 옵션을 주문해도 재고가 정확히 차감됨을 테스트가 증명한다 (Pessimistic Lock)
- [ ] `OptionRepository.findByIdWithLock()`이 `SELECT FOR UPDATE`로 실행된다
- [ ] 전체 테스트 Green
