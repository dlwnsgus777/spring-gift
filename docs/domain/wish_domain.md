# 위시리스트 도메인 분석

> 작성일: 2026-05-13  
> 최종 수정일: 2026-05-15  
> 분석 대상 브랜치: feature/youth-6-fifth-assignment

---

## 변경 히스토리

| 날짜 | 내용 |
|------|------|
| 2026-05-13 | 최초 분석 |
| 2026-05-15 | WishCommandService / WishQueryService 분리; WishResult 추가; 주문 시 위시 자동 삭제(deleteByMemberIdAndProductId) 구현 |

---

## 1. 파일 구성

```
src/main/java/gift/wish/
├── Wish.java               — 위시리스트 엔티티 (회원 ID + 상품 참조)
├── WishController.java     — 위시리스트 REST API 컨트롤러
├── WishCommandService.java — 위시 추가·삭제·주문 시 자동 삭제 (@Transactional)
├── WishQueryService.java   — 위시 조회 (@Transactional(readOnly=true))
├── WishRepository.java     — JPA 리포지토리 (회원별 조회, 중복 체크)
├── WishRequest.java        — 위시 추가 요청 DTO (productId)
├── WishResponse.java       — 위시 응답 DTO (위시 ID + 상품 요약 정보)
└── WishResult.java         — 위시 추가 결과 (Wish + isNew 플래그)
```

테스트 파일: 없음

---

## 2. 비즈니스 로직

위시리스트는 회원이 관심 있는 상품을 저장해두는 기능으로, 회원별로 상품을 즐겨찾기처럼 등록·삭제·조회할 수 있다.

### 조회

인증된 회원 본인의 위시리스트만 조회할 수 있다. 결과는 페이지네이션으로 반환되며, 각 항목에는 위시 ID와 함께 상품 ID·이름·가격·이미지 URL이 포함된다.

### 추가

위시 추가 요청에는 반드시 상품 ID(`productId`)가 포함되어야 한다. 존재하지 않는 상품 ID로 요청하면 404를 반환한다. 동일 회원이 동일 상품을 이미 위시리스트에 추가한 경우, 중복 생성하지 않고 기존 위시 항목을 그대로 200 OK로 반환한다(멱등 처리). 신규 추가 성공 시 201 Created와 함께 생성된 위시 정보를 반환한다.

추가 결과는 `WishResult(wish, isNew)` 레코드로 반환되며, 컨트롤러가 `isNew` 플래그를 보고 201/200을 분기한다.

### 삭제

위시 ID로 삭제를 요청한다. 존재하지 않는 위시 ID면 404를 반환한다. 해당 위시의 소유자가 아닌 다른 회원이 삭제를 시도하면 403 Forbidden을 반환한다. 본인 소유의 위시만 삭제할 수 있으며, 성공 시 204 No Content를 반환한다.

### 주문 시 자동 삭제

주문 생성 시 `OrderCommandService`가 `WishCommandService.deleteByMemberIdAndProductId(memberId, productId)`를 호출해 해당 상품의 위시 항목을 자동 삭제한다. 위시에 담겨 있지 않은 경우 무시된다.

### 인증

모든 API는 `Authorization` 헤더의 JWT 토큰으로 회원을 식별한다. `AuthService.extractMember()`가 토큰을 검증하고 Member를 반환한다. 유효하지 않은 토큰이거나 헤더가 없으면 401 Unauthorized를 반환한다.

### 불변식

- `member_id`와 `product_id`는 모두 NOT NULL이다. 회원과 상품 없이 위시리스트 항목을 생성할 수 없다.
- `(member_id, product_id)` 조합의 중복은 DB 제약이 아닌 애플리케이션 레벨에서 방지한다.
- 위시리스트 항목은 참조하는 회원(`member`)이나 상품(`product`)이 삭제될 때 CASCADE 삭제 설정이 없으므로, 참조 대상 삭제 전에 위시 항목을 먼저 처리해야 한다.

---

## 3. 데이터 모델

### DB 스키마 (V1__Initialize_project_tables.sql)

```sql
create table wish
(
    id         bigint auto_increment primary key,
    member_id  bigint not null,
    product_id bigint not null,
    foreign key (member_id) references member (id),
    foreign key (product_id) references product (id)
);
```

### 기본 데이터 (V2__Insert_default_data.sql)

| member_id | product_id |
|-----------|------------|
| 2         | 1          |
| 2         | 3          |
| 3         | 2          |
| 3         | 5          |

---

## 4. 도메인 객체

```java
@Entity
public class Wish {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long memberId;          // primitive FK (느슨한 참조, Member 엔티티 미참조)

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;        // 강한 참조 (상품 정보 즉시 로딩)

    protected Wish() {}
    public Wish(Long memberId, Product product) { ... }
    public Long getId() { ... }
    public Long getMemberId() { ... }
    public Product getProduct() { ... }
}
```

### WishResult

```java
public record WishResult(Wish wish, boolean isNew) {}
```

`WishCommandService.addWish()`가 반환하며, 컨트롤러가 `isNew` 플래그로 201/200 응답 코드를 분기한다.

### 공개 API

#### WishCommandService

| 메서드 | 설명 |
|--------|------|
| `addWish(Long memberId, Long productId)` | 위시 추가. 중복이면 기존 항목 반환(isNew=false) |
| `removeWish(Long memberId, Long wishId)` | 위시 삭제. 소유자 불일치 시 403 |
| `deleteByMemberIdAndProductId(Long memberId, Long productId)` | 주문 시 위시 자동 삭제. 없으면 무시 |

#### WishQueryService

| 메서드 | 설명 |
|--------|------|
| `findByMemberId(Long memberId, Pageable pageable)` | 회원의 위시 목록 페이지 조회 |

---

## 5. API 명세

| HTTP | 경로 | 동작 | 요청 | 성공 응답 |
|------|------|------|------|----------|
| GET | `/api/wishes` | 내 위시리스트 페이지 조회 | `Pageable` 쿼리 파라미터 | 200 + `Page<WishResponse>` |
| POST | `/api/wishes` | 위시 추가 | `WishRequest` (productId) | 201 Created (신규) / 200 OK (중복) |
| DELETE | `/api/wishes/{id}` | 위시 삭제 | 위시 ID (path) | 204 No Content |

모든 엔드포인트는 `Authorization` 헤더에 JWT 토큰을 필요로 하며, 토큰이 없거나 유효하지 않으면 401을 반환한다.

---

## 6. DTO 설계

**WishRequest**

```java
public record WishRequest(@NotNull Long productId) {}
```

위시 추가 시 사용. `productId`는 필수값이며 null이면 400 Bad Request가 반환된다.

---

**WishResponse**

```java
public record WishResponse(
    Long id,
    Long productId,
    String name,
    int price,
    String imageUrl
) {
    public static WishResponse from(Wish wish) { ... }
}
```

위시 항목과 연결된 상품의 요약 정보(이름·가격·이미지)를 함께 반환한다. `Wish` 엔티티에서 정적 팩토리 메서드로 변환한다.

---

## 7. 다른 도메인과의 관계

```
Member ──(memberId: Long)──► Wish ──(@ManyToOne)──► Product
                              │
                   위시 자동 삭제: OrderCommandService
                   → WishCommandService.deleteByMemberIdAndProductId()
```

- `Wish.memberId`는 `Long` 타입으로 `Member` 엔티티를 직접 참조하지 않는다(느슨한 FK). 회원 정보가 필요하면 별도 쿼리가 필요하다.
- `Wish.product`는 `@ManyToOne`으로 `Product` 엔티티를 강하게 참조한다. 위시 조회 시 상품 정보가 함께 로딩된다.
- `wish` 테이블의 `member_id`, `product_id` 컬럼 모두 FK 제약이 걸려 있으나 `ON DELETE CASCADE`가 없으므로, 회원이나 상품을 삭제하기 전에 관련 위시 항목을 먼저 삭제해야 한다.
- 주문 생성(`OrderCommandService.createOrder()`) 시 해당 상품의 위시를 자동 삭제한다. 위시에 담겨 있지 않으면 무시(`ifPresent` 처리)된다.
