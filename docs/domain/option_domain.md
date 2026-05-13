# 옵션 도메인 분석

> 작성일: 2026-05-13  
> 분석 대상 브랜치: feature/youth-6-fifth-assignment

---

## 1. 파일 구성

```
src/main/java/gift/option/
├── Option.java              — 옵션 엔티티, 재고 차감 도메인 로직 포함
├── OptionController.java    — 상품별 옵션 CRUD REST API
├── OptionNameValidator.java — 옵션 이름 유효성 검사 (문자 종류, 길이)
├── OptionRepository.java    — 상품 ID 기반 조회, 이름 중복 확인 쿼리
├── OptionRequest.java       — 옵션 생성 요청 DTO (Record)
└── OptionResponse.java      — 옵션 응답 DTO (Record)
```

테스트 파일: 없음

---

## 2. 비즈니스 로직

옵션은 상품의 SKU(Stock Keeping Unit) 역할을 하며, 하나의 상품에 1개 이상 반드시 존재해야 하는 재고 단위이다.

### 옵션 조회

특정 상품에 속한 모든 옵션 목록을 반환한다. 상품이 존재하지 않으면 404를 반환한다.

### 옵션 생성

옵션을 생성하기 위해서는 다음 조건을 모두 충족해야 한다.

- 상품이 존재해야 한다. 존재하지 않으면 404를 반환한다.
- 옵션 이름은 공백을 포함하여 최대 50자이다.
- 옵션 이름에는 한글, 영문, 숫자, 공백, 그리고 `( ) [ ] + - & / _` 특수문자만 허용된다.
- 같은 상품 내에서 옵션 이름은 중복될 수 없다. 중복 시 400을 반환한다.
- 수량은 최소 1, 최대 99,999,999이다.

### 옵션 삭제

옵션을 삭제하기 위해서는 다음 조건을 충족해야 한다.

- 상품이 존재해야 한다. 존재하지 않으면 404를 반환한다.
- 해당 상품의 옵션이 1개만 남아 있으면 삭제할 수 없다. 삭제 시 400을 반환한다. 상품에는 항상 최소 하나의 옵션이 존재해야 한다.
- 삭제 대상 옵션이 존재하지 않거나 해당 상품에 속하지 않으면 404를 반환한다.

### 재고 차감

주문 처리 시 옵션의 재고가 차감된다. 차감 수량이 현재 재고보다 크면 예외가 발생하며, 재고는 절대로 음수가 되지 않는다. 이 규칙은 `Option.subtractQuantity()` 메서드 안에 캡슐화되어 있다.

---

## 3. 데이터 모델

### DB 스키마 (V1__Initialize_project_tables.sql)

```sql
create table options
(
    id         bigint auto_increment primary key,
    product_id bigint      not null,
    name       varchar(50) not null,
    quantity   int         not null,
    foreign key (product_id) references product (id)
);
```

### 기본 데이터 (V2__Insert_default_data.sql)

| product_id | name | quantity |
|-----------|------|----------|
| 1 | 스페이스 블랙 / M1 Pro | 10 |
| 1 | 실버 / M1 Max | 5 |
| 2 | 블루 / 256GB | 30 |
| 2 | 블랙 / 512GB | 20 |
| 3 | 270mm | 15 |
| 4 | 32인치 | 25 |
| 5 | 일반 감귤 | 50 |
| 6 | 1++ 등급 | 8 |

---

## 4. 도메인 객체

```java
@Entity
@Table(name = "options")
public class Option {
    private Long id;
    private Product product;   // @ManyToOne, NOT NULL
    private String name;       // NOT NULL, max 50
    private int quantity;      // NOT NULL

    public Option(Product product, String name, int quantity)
    public void subtractQuantity(int amount)  // 재고 부족 시 IllegalArgumentException
    public Long getId()
    public Product getProduct()
    public String getName()
    public int getQuantity()
}
```

### 공개 API

| 메서드 | 설명 |
|--------|------|
| `subtractQuantity(int amount)` | 지정 수량만큼 재고를 차감한다. 차감량이 현재 재고보다 크면 예외를 던진다. |
| `getId()` | 옵션 ID를 반환한다. |
| `getProduct()` | 옵션이 속한 상품 엔티티를 반환한다. |
| `getName()` | 옵션 이름을 반환한다. |
| `getQuantity()` | 현재 재고 수량을 반환한다. |

---

## 5. API 명세

| HTTP | 경로 | 동작 | 요청 | 성공 응답 |
|------|------|------|------|----------|
| GET | `/api/products/{productId}/options` | 상품의 옵션 목록 조회 | 없음 | 200 `List<OptionResponse>` |
| POST | `/api/products/{productId}/options` | 옵션 생성 | `OptionRequest` (JSON Body) | 201 `OptionResponse` |
| DELETE | `/api/products/{productId}/options/{optionId}` | 옵션 삭제 | 없음 | 204 |

인증 없이 접근 가능한 API이다. (`@AuthenticationPrincipal` 또는 Authorization 헤더 사용 없음)

---

## 6. DTO 설계

### OptionRequest

```java
public record OptionRequest(
    @NotBlank String name,
    @Min(1) @Max(99_999_999) int quantity
)
```

옵션 생성 시 사용한다. Bean Validation으로 이름 공백 여부와 수량 범위를 검증하며, 이름 문자 종류 검증은 `OptionNameValidator`에서 별도로 수행한다.

### OptionResponse

```java
public record OptionResponse(
    Long id,
    String name,
    int quantity
)
```

상품 ID는 URL 경로에 포함되어 있으므로 응답 본문에 포함하지 않는다.

---

## 7. 다른 도메인과의 관계

```
Product ──< Option >── Order
  (1)        (N)  (1)   (N)
```

- `Option`은 `Product`에 `@ManyToOne`으로 참조된다. `product_id`는 NOT NULL이므로 옵션은 반드시 하나의 상품에 속해야 한다.
- `orders` 테이블은 `option_id`로 `options`를 참조한다. `ON DELETE CASCADE`가 없으므로 주문이 존재하는 옵션은 DB 레벨에서 직접 삭제할 수 없다.
- 주문(`Order`) 생성 시 `Option.subtractQuantity()`가 호출되어 재고가 차감된다. 재고 부족 시 주문 처리가 중단된다.
- 상품(`Product`) 삭제 시 연결된 옵션도 함께 처리해야 하나, `ON DELETE CASCADE`가 없으므로 애플리케이션 레이어에서 순서를 보장해야 한다.
