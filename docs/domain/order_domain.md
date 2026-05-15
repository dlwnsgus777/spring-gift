# 주문 도메인 분석

> 작성일: 2026-05-14  
> 최종 수정일: 2026-05-15  
> 분석 대상 브랜치: feature/youth-6-fifth-assignment

---

## 변경 히스토리

| 날짜 | 내용 |
|------|------|
| 2026-05-14 | 최초 분석 — KakaoMessageClient가 order 패키지에 위치, 위시 삭제 미구현, @Transactional 누락 버그 |
| 2026-05-15 | OrderCommandService 분리(@Transactional 수정); 위시 자동 삭제 구현 완료; KakaoMessageClient → notification 패키지로 이동; NotifySendService 추가 |

---

## 1. 파일 구성

```
src/main/java/gift/order/
├── Order.java               — 주문 엔티티 (옵션·회원·수량·메시지·주문일시)
├── OrderController.java     — 주문 조회 / 주문 생성 API
├── OrderCommandService.java — 주문 생성 트랜잭션 처리 (재고 차감·포인트 차감·위시 삭제·저장)
├── OrderQueryService.java   — 주문 조회 (@Transactional(readOnly=true))
├── OrderRepository.java     — 주문 저장소 (회원별 페이징 조회)
├── OrderRequest.java        — 주문 생성 요청 DTO
└── OrderResponse.java       — 주문 응답 DTO

src/main/java/gift/notification/
├── MessageClient.java       — 알림 발송 인터페이스
├── KakaoMessageClient.java  — 카카오 나에게 메시지 발송 구현체
└── NotifySendService.java   — 알림 발송 가능 여부 판단 후 발송 위임
```

테스트 파일: 없음

---

## 2. 비즈니스 로직

주문 도메인은 회원이 상품 옵션을 구매하는 행위를 처리하며, 재고 차감·포인트 결제·위시 자동 삭제·카카오 알림 발송을 단일 트랜잭션 흐름 안에서 조율한다.

### 주문 생성

주문을 생성하려면 유효한 JWT 토큰이 필요하다. 토큰이 없거나 유효하지 않으면 401을 반환한다.

`OrderCommandService.createOrder()`가 `@Transactional`로 보호되어 다음 순서를 원자적으로 처리한다:

1. **재고 차감**: `OptionRepository.findByIdWithLock()`으로 비관적 락(Pessimistic Write Lock)을 획득한 뒤 `Option.subtractQuantity(quantity)`를 호출한다. 요청 수량이 현재 재고를 초과하면 `IllegalArgumentException`이 발생한다.
2. **포인트 차감**: `Member.deductPoint(price * quantity)`로 포인트를 차감한다. 잔액이 부족하면 `IllegalArgumentException`이 발생하며, 재고 차감도 함께 롤백된다.
3. **위시 삭제**: 해당 회원이 주문한 상품을 위시리스트에 담아 놓았다면 자동으로 삭제한다(`WishCommandService.deleteByMemberIdAndProductId()`).
4. **주문 저장**: 위 단계가 모두 성공하면 주문을 저장하고 `Order` 엔티티를 반환한다.

주문 저장 후 `OrderController`에서 `NotifySendService.sendIfPossible(member, order)`를 호출해 카카오 알림을 발송한다. 알림 발송은 트랜잭션 외부에서 best-effort로 시도되며, 실패해도 주문 결과에 영향을 주지 않는다.

메시지는 선택 사항이다. 요청에 포함하지 않으면 `null`로 저장된다.

### 주문 조회

유효한 JWT 토큰이 있는 회원만 자신의 주문 목록을 조회할 수 있다. 토큰이 없거나 유효하지 않으면 401을 반환한다.

조회 결과는 페이징을 지원하며, `Pageable` 파라미터(`page`, `size`, `sort`)를 쿼리 스트링으로 전달한다. 다른 회원의 주문은 조회되지 않는다.

---

## 3. 데이터 모델

### DB 스키마 (V1__Initialize_project_tables.sql)

```sql
create table orders
(
    id              bigint auto_increment primary key,
    option_id       bigint    not null,
    member_id       bigint    not null,
    quantity        int       not null,
    message         varchar(255),
    order_date_time timestamp not null,
    foreign key (option_id) references options (id),
    foreign key (member_id) references member (id)
);
```

### 기본 데이터 (V2__Insert_default_data.sql)

| id | option_id | member_id | quantity | message | order_date_time |
|----|-----------|-----------|----------|---------|-----------------|
| 1 | 3 | 2 | 1 | 생일 축하해! 🎉 | 2026-02-10 14:30:00 |
| 2 | 5 | 2 | 2 | null | 2026-02-12 09:15:00 |
| 3 | 7 | 3 | 1 | 엄마 감사합니다 ❤️ | 2026-02-14 18:00:00 |
| 4 | 8 | 3 | 1 | 맛있게 드세요! | 2026-02-15 11:45:00 |

---

## 4. 도메인 객체

```java
@Entity
@Table(name = "orders")
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "option_id")
    private Option option;

    private Long memberId;          // primitive FK — JPA 관계 미매핑, N+1 방지 의도적 설계
    private int quantity;
    private String message;         // nullable
    private LocalDateTime orderDateTime;

    public Order(Option option, Long memberId, int quantity, String message) { ... }
}
```

### 공개 API

| 메서드 | 설명 |
|--------|------|
| `getId()` | 주문 ID 반환 |
| `getOption()` | 연관 Option 반환 |
| `getMemberId()` | 주문한 회원 ID 반환 |
| `getQuantity()` | 주문 수량 반환 |
| `getMessage()` | 주문 메시지 반환 (nullable) |
| `getOrderDateTime()` | 주문 일시 반환 |

---

## 5. API 명세

| HTTP | 경로 | 동작 | 요청 | 성공 응답 |
|------|------|------|------|----------|
| GET | `/api/orders` | 내 주문 목록 조회 (페이징) | `Pageable` 쿼리 파라미터 | 200 `Page<OrderResponse>` |
| POST | `/api/orders` | 주문 생성 | `OrderRequest` (JSON Body) | 201 `OrderResponse` + Location 헤더 |

모든 엔드포인트는 `Authorization` 헤더에 유효한 Bearer JWT 토큰이 필요하다. 토큰이 없거나 유효하지 않으면 401을 반환한다.

---

## 6. DTO 설계

### OrderRequest

```java
public record OrderRequest(
    @NotNull Long optionId,    // 구매할 옵션 ID (필수)
    @Min(1) int quantity,      // 주문 수량 (최소 1)
    String message             // 선물 메시지 (선택)
) {}
```

`optionId`가 null이거나 `quantity`가 1 미만이면 400을 반환한다. `message`는 검증하지 않으며 null 허용.

### OrderResponse

```java
public record OrderResponse(
    Long id,
    Long optionId,
    int quantity,
    LocalDateTime orderDateTime,
    String message
) {
    public static OrderResponse from(Order order) { ... }
}
```

응답에 회원 정보는 포함되지 않는다. 옵션은 ID만 노출하며 상품·가격 정보는 포함하지 않는다.

---

## 7. 다른 도메인과의 관계

```
Member ──(Long memberId, 논리적 참조 — JPA 관계 미매핑)──► Order
  │
  │  OrderCommandService.createOrder() (@Transactional)
  │  ├─ 1. OptionRepository.findByIdWithLock() → Option.subtractQuantity()  [재고 차감 + 비관적 락]
  │  ├─ 2. MemberRepository → Member.deductPoint()                          [포인트 차감]
  │  ├─ 3. WishCommandService.deleteByMemberIdAndProductId()                [위시 자동 삭제]
  │  └─ 4. orderRepository.save(new Order(...))                             [주문 저장]
  │
  └─ OrderController (트랜잭션 외부)
       └─ NotifySendService.sendIfPossible(member, order)                   [카카오 알림, best-effort]
```

- `Order.option`은 `@ManyToOne` JPA 관계로 매핑된다. 주문 생성 시 가격 계산을 위해 `option.getProduct().getPrice()`를 호출한다.
- `Order.memberId`는 `Long` 타입 primitive FK다. DB에는 `member(id)` FK 제약이 존재하지만 JPA 관계는 매핑하지 않는다. 주문 조회 시 Member를 자동 로딩하는 N+1을 방지하기 위한 의도적 설계다.
- `OptionRepository.findByIdWithLock()`은 `@Lock(LockModeType.PESSIMISTIC_WRITE)`로 동시 주문 시 재고 차감 정합성을 보장한다.
- `NotifySendService`는 `notification` 패키지에 위치하며, `member.getKakaoAccessToken()`이 null이면 발송을 건너뛴다.
