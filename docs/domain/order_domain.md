# 주문 도메인 분석

> 작성일: 2026-05-14  
> 분석 대상 브랜치: feature/youth-6-fifth-assignment

---

## 1. 파일 구성

```
src/main/java/gift/order/
├── Order.java               — 주문 엔티티 (옵션·회원·수량·메시지·주문일시)
├── OrderController.java     — 주문 조회 / 주문 생성 API
├── OrderRepository.java     — 주문 저장소 (회원별 페이징 조회)
├── OrderRequest.java        — 주문 생성 요청 DTO
├── OrderResponse.java       — 주문 응답 DTO
└── KakaoMessageClient.java  — 주문 완료 후 카카오 나에게 메시지 발송 클라이언트
```

테스트 파일: 없음

---

## 2. 비즈니스 로직

주문 도메인은 회원이 상품 옵션을 구매하는 행위를 처리하며, 재고 차감·포인트 결제·카카오 알림 발송을 단일 흐름 안에서 조율한다.

### 주문 생성

주문을 생성하려면 유효한 JWT 토큰이 필요하다. 토큰이 없거나 유효하지 않으면 401을 반환한다.

요청한 옵션 ID가 존재하지 않으면 404를 반환하고 처리를 중단한다.

옵션 재고 차감이 먼저 수행된다. 요청 수량이 현재 재고를 초과하면 `IllegalArgumentException`이 발생하며 주문이 생성되지 않는다.

포인트 결제는 `옵션이 속한 상품 가격 × 주문 수량`으로 계산된다. 회원의 잔여 포인트가 결제 금액보다 적으면 `IllegalArgumentException`이 발생하며 주문이 생성되지 않는다.

재고 차감과 포인트 차감이 성공하면 주문이 저장된다. 주문일시(`orderDateTime`)는 저장 시점 서버 시각으로 자동 설정된다.

메시지는 선택 사항이다. 요청에 포함하지 않으면 `null`로 저장된다.

주문 저장 후 카카오 알림 발송이 시도된다. 회원에게 Kakao Access Token이 없으면 발송을 건너뛴다. 발송 중 예외가 발생해도 무시하며, 알림 실패는 주문 결과에 영향을 주지 않는다.

`wishRepository`가 컨트롤러에 주입되어 있고 처리 흐름 주석에 "cleanup wish" 단계가 명시되어 있으나, 현재 코드에 실제 위시리스트 삭제 호출은 없다.

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
Member ──────────────────────────────────────────────────────┐
  │ (Long memberId, 논리적 참조 — JPA 관계 미매핑)                   │
  │                                                           ▼
  │          Option ──(ManyToOne)── Product              Order
  │            │                                           │
  │  주문 생성 시 option.subtractQuantity(quantity)         │
  │  주문 생성 시 member.deductPoint(price)                  │
  └───────────────────────────────────────────────────────┘

WishRepository (주입 O, 호출 X)
KakaoMessageClient ← Order + Product (주문 완료 알림, best-effort)
```

- `Order.option`은 `@ManyToOne` JPA 관계로 매핑된다. 주문 생성 시 가격 계산을 위해 `option.getProduct().getPrice()`를 호출한다.
- `Order.memberId`는 `Long` 타입 primitive FK다. DB에는 `member(id)` FK 제약이 존재하지만 JPA 관계는 매핑하지 않는다. 주문 조회 시 Member를 자동 로딩하는 N+1을 방지하기 위한 의도적 설계다.
- 주문 생성은 Option과 Member 두 도메인의 상태를 변경한다. `Option.subtractQuantity()`로 재고를 차감하고, `Member.deductPoint()`로 포인트를 차감한다.
- `wishRepository`가 컨트롤러에 주입되어 있으나 현재 주문 생성 흐름에서 호출되지 않는다.
- `KakaoMessageClient`는 회원의 Kakao Access Token이 있을 때만 호출된다. 발송 실패는 예외를 무시하고 주문 결과에 영향을 주지 않는다.
