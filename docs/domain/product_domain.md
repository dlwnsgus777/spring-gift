# 상품 도메인 분석

> 작성일: 2026-05-11  
> 분석 대상 브랜치: feature/youth-6-fifth-assignment

---

## 1. 파일 구성

```
src/main/java/gift/product/
├── Product.java                  — 상품 엔티티 (이름·가격·이미지·카테고리·옵션 보유)
├── ProductNameValidator.java     — 상품명 유효성 검사 로직 (길이·문자·카카오 제한)
├── ProductRepository.java        — JPA Repository (표준 CRUD)
├── ProductRequest.java           — REST 생성/수정 요청 DTO (Record + Jakarta Validation)
├── ProductResponse.java          — REST 응답 DTO (Record)
├── ProductController.java        — REST API 컨트롤러 (/api/products)
└── AdminProductController.java   — 관리자 화면 컨트롤러 (Thymeleaf, /admin/products)
```

테스트 파일: 없음

---

## 2. 비즈니스 로직

상품 도메인은 선물하기 플랫폼에서 판매 가능한 상품 목록을 관리하며, 각 상품은 하나의 카테고리에 속하고 하나 이상의 옵션(SKU)을 가질 수 있다.

### 상품명 유효성 규칙

상품명은 생성·수정 시 `ProductNameValidator`를 통해 공통으로 검증된다.

- 상품명은 필수값이며, 공백(blank)은 허용하지 않는다.
- 공백을 포함한 전체 길이가 15자를 초과할 수 없다. DB의 `name varchar(15)` 제약과 일치한다.
- 허용되는 문자는 영문 대·소문자, 숫자, 한글(완성형·자모), 공백, 그리고 `( ) [ ] + - & / _` 7종의 특수문자에 한정된다.
- "카카오"가 포함된 상품명은 일반 REST API 경로에서는 사용할 수 없다. 담당 MD와 협의된 경우에만 Admin 화면을 통해 등록할 수 있다 (`allowKakao=true` 플래그로 분기).

### 상품 생성

REST API(`POST /api/products`)와 Admin 화면(`POST /admin/products`) 두 경로로 상품을 생성할 수 있다. 두 경로 모두 이름 유효성 검사를 먼저 수행하며, 요청에 포함된 `categoryId`에 해당하는 카테고리가 존재하지 않으면 생성을 거부하고 404를 반환한다(Admin은 예외를 던져 전역 핸들러 처리). 모든 상품은 생성 시점부터 반드시 카테고리에 속해야 한다.

### 상품 조회

REST API는 페이지네이션(`Pageable`) 기반 목록 조회와 단건 조회를 제공한다. Admin 화면은 페이지 없이 전체 목록을 조회해 뷰에 전달한다. 단건 조회 시 존재하지 않는 id이면 REST는 404를 반환하고, Admin 편집 폼 조회는 `NoSuchElementException`을 던진다.

### 상품 수정

이름·가격·이미지URL·카테고리를 한 번에 일괄 수정한다(`Product.update()`). 부분 수정 없이 전체 필드를 덮어쓴다. 수정 전에도 이름 유효성 검사와 카테고리 존재 여부를 확인한다.

### 상품 삭제

상품을 삭제하면 연관된 옵션이 함께 삭제된다(`CascadeType.ALL + orphanRemoval = true`). 반면 wish 테이블은 `product_id`를 외래키로 참조하지만 CASCADE 설정이 없으므로, 위시리스트에 담긴 상품을 삭제하면 DB 외래키 제약 위반이 발생한다. REST API의 `DELETE /api/products/{id}`는 존재하지 않는 id에 대해서도 204를 반환한다.

---

## 3. 데이터 모델

### DB 스키마 (V1__Initialize_project_tables.sql)

```sql
create table product
(
    id          bigint auto_increment primary key,
    name        varchar(15)  not null,
    price       int          not null,
    image_url   varchar(255) not null,
    category_id bigint       not null,
    foreign key (category_id) references category (id)
);
```

### 기본 데이터 (V2__Insert_default_data.sql)

| id | name | price | category |
|----|------|-------|----------|
| 1 | 맥북 프로 16인치 | 3,360,000 | 전자기기 |
| 2 | 아이폰 16 | 1,350,000 | 전자기기 |
| 3 | 나이키 에어맥스 | 179,000 | 패션 |
| 4 | 레비스 청바지 | 89,000 | 패션 |
| 5 | 제주 감귤 5kg | 25,000 | 식품 |
| 6 | 한우 등심 1kg | 65,000 | 식품 |

---

## 4. 도메인 객체

```java
@Entity
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private int price;
    private String imageUrl;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Option> options = new ArrayList<>();

    public Product(String name, int price, String imageUrl, Category category) { ... }
    public void update(String name, int price, String imageUrl, Category category) { ... }

    // getters: getId(), getName(), getPrice(), getImageUrl(), getCategory(), getOptions()
}
```

### 공개 API

| 메서드 | 설명 |
|--------|------|
| `update(name, price, imageUrl, category)` | 상품 전체 필드를 일괄 수정한다 |
| `getOptions()` | 연관된 옵션 목록을 반환한다 |

---

## 5. API 명세

### REST API (`/api/products`)

| HTTP | 경로 | 동작 | 요청 | 성공 응답 |
|------|------|------|------|----------|
| GET | `/api/products` | 상품 목록 조회 (페이지네이션) | Pageable 쿼리 파라미터 | 200 + Page&lt;ProductResponse&gt; |
| GET | `/api/products/{id}` | 상품 단건 조회 | — | 200 + ProductResponse |
| POST | `/api/products` | 상품 생성 | JSON ProductRequest | 201 + ProductResponse |
| PUT | `/api/products/{id}` | 상품 전체 수정 | JSON ProductRequest | 200 + ProductResponse |
| DELETE | `/api/products/{id}` | 상품 삭제 | — | 204 No Content |

인증 불필요 — `@AuthenticationPrincipal` 미사용.

### Admin API (`/admin/products`)

| HTTP | 경로 | 동작 | 응답 |
|------|------|------|------|
| GET | `/admin/products` | 상품 목록 페이지 | product/list 뷰 |
| GET | `/admin/products/new` | 상품 등록 폼 | product/new 뷰 |
| POST | `/admin/products` | 상품 등록 처리 | redirect:/admin/products |
| GET | `/admin/products/{id}/edit` | 상품 수정 폼 | product/edit 뷰 |
| POST | `/admin/products/{id}/edit` | 상품 수정 처리 | redirect:/admin/products |
| POST | `/admin/products/{id}/delete` | 상품 삭제 처리 | redirect:/admin/products |

---

## 6. DTO 설계

### ProductRequest (생성·수정 요청)

```java
public record ProductRequest(
    @NotBlank String name,
    @Positive int price,
    @NotBlank String imageUrl,
    @NotNull Long categoryId
) {
    public Product toEntity(Category category) { ... }
}
```

- Jakarta Validation으로 필드 형식을 먼저 검증한 뒤, `ProductNameValidator`로 이름 규칙을 추가 검증한다.
- `categoryId`를 받고, 컨트롤러에서 Category 엔티티로 변환해 `toEntity(category)`에 전달한다.

### ProductResponse (응답)

```java
public record ProductResponse(
    Long id,
    String name,
    int price,
    String imageUrl,
    Long categoryId
) {
    public static ProductResponse from(Product product) { ... }
}
```

- Category 객체 대신 `categoryId`(Long)만 노출한다. 카테고리 상세 정보가 필요하면 별도 카테고리 API를 호출해야 한다.

---

## 7. 다른 도메인과의 관계

```
Category ──(1:N)──> Product ──(1:N, CASCADE ALL)──> Option ──(1:N)──> Order
                       △
                       │ FK (CASCADE 없음)
                      Wish
```

- 모든 상품은 반드시 하나의 카테고리에 속해야 한다 (`category_id NOT NULL`, FK).
- 상품 삭제 시 연관된 모든 옵션이 자동으로 삭제된다 (`CascadeType.ALL + orphanRemoval`).
- 위시리스트(`Wish`)는 상품을 FK로 참조하지만 CASCADE 설정이 없어, 위시에 담긴 상품은 DB 레벨에서 삭제가 차단된다.
- 주문(`Order`)은 옵션을 통해 간접적으로 상품과 연결된다. 옵션이 삭제되면 주문의 FK 제약도 위반된다.
