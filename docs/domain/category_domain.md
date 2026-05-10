# Category 도메인 분석

> 작성일: 2026-05-09  
> 분석 대상 브랜치: feature/youth-6-fifth-assignment

---

## 1. 파일 구성

```
src/main/java/gift/category/
├── Category.java           — JPA Entity
├── CategoryController.java — REST Controller
├── CategoryRepository.java — Spring Data JPA Repository
├── CategoryRequest.java    — 요청 DTO (Java Record)
└── CategoryResponse.java   — 응답 DTO (Java Record)
```

테스트 파일: 없음

---

## 2. 비즈니스 로직

Category는 상품을 분류하는 단위다. 모든 상품은 반드시 하나의 카테고리에 속해야 한다.

### 카테고리 생성

카테고리 이름은 시스템 전체에서 유일해야 한다. 같은 이름의 카테고리를 두 번 생성하면 DB 유니크 제약 위반으로 실패한다. 이름, 색상, 이미지 URL은 필수값이고 설명은 선택이다.

### 카테고리 수정

카테고리의 모든 속성(이름, 색상, 이미지 URL, 설명)을 한 번에 교체한다. 부분 수정은 지원하지 않는다. 존재하지 않는 id로 수정을 요청하면 404를 반환한다.

### 카테고리 삭제

카테고리를 삭제하려면 해당 카테고리를 참조하는 상품이 없어야 한다. 상품이 남아 있는 상태에서 삭제를 시도하면 FK 제약 위반으로 실패한다. 카테고리와 상품 사이에 `ON DELETE CASCADE`는 없다 — 상품이 있는 카테고리는 삭제할 수 없다는 비즈니스 규칙을 DB 제약으로 강제하는 설계다.

### 카테고리 조회

전체 카테고리 목록을 페이징 없이 반환한다. 카테고리 API는 인증 없이 접근 가능하다.

---

## 3. 데이터 모델

### DB 스키마 (V1__Initialize_project_tables.sql)

```sql
create table category (
    id          bigint auto_increment primary key,
    name        varchar(255) not null unique,
    color       varchar(7)   not null,
    image_url   varchar(255) not null,
    description varchar(255)          -- nullable
);
```

### 기본 데이터 (V2__Insert_default_data.sql)

| id | name   | color   | description              |
|----|--------|---------|--------------------------|
| 1  | 전자기기 | #1E90FF | 스마트폰, 노트북 등 전자기기 |
| 2  | 패션    | #FF6347 | 의류, 신발, 액세서리         |
| 3  | 식품    | #32CD32 | 신선식품, 가공식품, 음료      |

---

## 4. 도메인 객체 (Category.java)

```java
@Entity
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String color;
    private String imageUrl;
    private String description;   // nullable
}
```

### 공개 API

| 메서드 | 설명 |
|--------|------|
| `Category(name, color, imageUrl, description)` | 생성자 |
| `update(name, color, imageUrl, description)` | 전체 필드 교체 (Setter 대신 의미 있는 메서드) |
| `getId()` / `getName()` / `getColor()` / `getImageUrl()` / `getDescription()` | Getter |

---

## 5. API 명세 (CategoryController.java)

| HTTP | 경로 | 동작 | 요청 | 성공 응답 |
|------|------|------|------|----------|
| GET | `/api/categories` | 전체 조회 | — | 200 + `List<CategoryResponse>` |
| POST | `/api/categories` | 생성 | `CategoryRequest` | 201 + `CategoryResponse` (Location 헤더 포함) |
| PUT | `/api/categories/{id}` | 수정 | `CategoryRequest` | 200 + `CategoryResponse` |
| DELETE | `/api/categories/{id}` | 삭제 | — | 204 |

인증 없이 접근 가능하다. (상품·주문 API와 달리 `@AuthenticationPrincipal` 미사용)

---

## 6. DTO 설계

### CategoryRequest

```java
public record CategoryRequest(
    @NotBlank String name,      // 필수
    @NotBlank String color,     // 필수
    @NotBlank String imageUrl,  // 필수
    String description          // 선택
) {
    public Category toEntity() { ... }
}
```

- `toEntity()`로 Entity 변환 책임 보유
- `@Valid` + `@NotBlank`로 요청 검증

### CategoryResponse

```java
public record CategoryResponse(Long id, String name, String color, String imageUrl, String description) {
    public static CategoryResponse from(Category category) { ... }
}
```

- 정적 팩토리 메서드 `from(Category)` 패턴

---

## 7. 다른 도메인과의 관계

```
Category (1)
    └── Product (N)   — @ManyToOne @JoinColumn(name = "category_id") NOT NULL
```

- `Product`는 `Category`를 필수로 참조한다 (FK NOT NULL).
- 카테고리를 삭제하면 참조 중인 상품이 있을 경우 FK 제약 위반으로 실패한다.
- `ProductResponse`는 `categoryId` (Long)만 노출한다 — Category 객체 전체를 직렬화하지 않는다.
