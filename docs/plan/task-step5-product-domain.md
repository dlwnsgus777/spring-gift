# spec: 5단계 Product 도메인 — 통합 테스트 + Service 추출 + 도메인 책임 이동

## 1. 기능 개요

Product 도메인에 세 가지 작업을 순차적으로 적용한다.

1. **통합 테스트 작성**: `ProductController`·`AdminProductController`의 현재 동작을 Green 테스트로 고정한다.
2. **서비스 추출**: `ProductQueryService` + `ProductCommandService`로 분리하고, Controller의 직접 Repository 의존을 제거한다.
3. **도메인 책임 이동**: 상품명 포맷 검증(`ProductNameValidator`)을 `Product` 생성자 내부로 이동해 잘못된 상태의 Product가 생성되지 않도록 한다.

### 기능 구성

| 대상 | 유형 | 변경 내용 |
|------|------|----------|
| `ProductControllerTest.java` | 신규 | REST API 통합 테스트 (MockMvc) |
| `AdminProductControllerTest.java` | 신규 | Admin 화면 통합 테스트 (MockMvc) |
| `ProductTest.java` | 신규 | Product 엔티티 불변식 단위 테스트 |
| `ProductQueryService.java` | 신규 | 읽기 전용 서비스 (`findById`, `findAll`) |
| `ProductQueryServiceTest.java` | 신규 | ProductQueryService 통합 테스트 |
| `ProductCommandService.java` | 신규 | 상태 변경 서비스 (`create`, `update`, `delete`) |
| `ProductCommandServiceTest.java` | 신규 | ProductCommandService 통합 테스트 |
| `CategoryQueryService.java` | 수정 | `findById(Long id)` 추가 |
| `Product.java` | 수정 | 생성자에 이름 포맷 검증 추가 |
| `ProductController.java` | 수정 | `orElse(null)` 제거, Service 주입으로 교체 |
| `AdminProductController.java` | 수정 | Service 주입으로 교체 |

---

## 2. API 설계

API 변경 없음. 엔드포인트·요청·응답 스펙은 그대로 유지된다.

### REST API (`/api/products`)

```
GET  /api/products           — 페이지네이션 목록
GET  /api/products/{id}      — 단건 조회
POST /api/products           — 생성
PUT  /api/products/{id}      — 전체 수정
DELETE /api/products/{id}    — 삭제
```

**POST 요청 예시**
```json
{
  "name": "나이키 에어맥스",
  "price": 179000,
  "imageUrl": "https://example.com/airmax.jpg",
  "categoryId": 1
}
```

**성공 응답 (201)**
```json
{
  "id": 7,
  "name": "나이키 에어맥스",
  "price": 179000,
  "imageUrl": "https://example.com/airmax.jpg",
  "categoryId": 1
}
```

**에러 케이스**

| 상황 | 상태 코드 |
|------|----------|
| 존재하지 않는 `categoryId` | 404 |
| 이름 15자 초과 | 400 |
| 허용되지 않는 특수문자 | 400 |
| 일반 API에서 "카카오" 포함 | 400 |
| 존재하지 않는 `{id}` 조회/수정 | 404 |

---

## 3. 비즈니스 로직

### 3-1. 상품명 검증 규칙 분리

검증 로직을 **도메인 불변식**과 **컨텍스트 의존 규칙** 두 계층으로 분리한다.

| 규칙 | 위치 | 이유 |
|------|------|------|
| 필수값 / 최대 15자 / 허용 문자셋 | `Product` 생성자 | Product가 항상 보장해야 하는 불변식 |
| "카카오" 포함 금지 | `ProductCommandService` | Admin(허용) vs REST(금지)에 따라 달라지는 컨텍스트 의존 규칙 |

```
// before: 컨트롤러가 외부에서 검증 후 Product 생성
List<String> errors = ProductNameValidator.validate(name);
if (!errors.isEmpty()) throw ...;
new Product(name, ...);  // 검증 없이 생성 가능

// after: Product 생성자가 불변식 보장
new Product(name, ...);  // 생성자 내부에서 포맷 검증, 위반 시 IllegalArgumentException
```

### 3-2. 서비스 계층 흐름

**조회 (`ProductQueryService`)**
- `findById(id)`: Repository에서 조회. 없으면 `NoSuchElementException` → GlobalExceptionHandler가 404로 변환.
- `findAll(pageable)`: 페이지 기반 전체 조회.
- `findAll()`: Admin 목록 화면용 전체 조회 (페이지 없음).

**생성·수정·삭제 (`ProductCommandService`)**
1. `create(request)`: `CategoryQueryService.findById(categoryId)` → `Product` 생성자 호출 (포맷 검증 자동 실행) → 저장.
2. `update(id, request)`: Product 조회 → Category 조회 → `product.update()` → dirty checking 자동 저장.
3. `delete(id)`: `deleteById(id)`. 존재하지 않아도 예외 미발생.

**"카카오" 규칙 적용**
- `ProductCommandService`는 일반 사용자 컨텍스트: `validateKakao(name)` 호출.
- `AdminProductController`는 Admin 컨텍스트: "카카오" 검사 없이 `create`·`update` 호출.

### 3-3. `CategoryQueryService.findById()` 추가

현재 `CategoryQueryService`에는 `findById()`가 없다. `ProductCommandService`가 카테고리를 조회하려면 이 메서드가 필요하다.

```java
public Category findById(Long id) {
    return categoryRepository.findById(id)
        .orElseThrow(() -> new NoSuchElementException("카테고리가 존재하지 않습니다. id=" + id));
}
```

---

## 4. 구현 대상 파일

### 패키지 위치

```
src/main/java/gift/
├── category/
│   └── CategoryQueryService.java       (수정 — findById 추가)
└── product/
    ├── Product.java                    (수정 — 생성자 포맷 검증 추가)
    ├── ProductController.java          (수정 — Service 주입, null 체크 제거)
    ├── AdminProductController.java     (수정 — Service 주입)
    ├── ProductQueryService.java        (신규)
    └── ProductCommandService.java      (신규)

src/test/java/gift/
└── product/
    ├── ProductTest.java                (신규 — 순수 단위 테스트)
    ├── ProductControllerTest.java      (신규 — MockMvc 통합 테스트)
    ├── AdminProductControllerTest.java (신규 — MockMvc 통합 테스트)
    ├── ProductQueryServiceTest.java    (신규 — @Transactional 통합 테스트)
    └── ProductCommandServiceTest.java  (신규 — @Transactional 통합 테스트)
```

### 코드 스니핏

**`CategoryQueryService.java` (수정 — findById 추가)**

```java
public Category findById(Long id) {
    return categoryRepository.findById(id)
        .orElseThrow(() -> new NoSuchElementException("카테고리가 존재하지 않습니다. id=" + id));
}
```

---

**`Product.java` (수정 — 생성자 포맷 검증)**

```java
public Product(String name, int price, String imageUrl, Category category) {
    List<String> errors = ProductNameValidator.validate(name);
    if (!errors.isEmpty()) {
        throw new IllegalArgumentException(String.join(", ", errors));
    }
    this.name = name;
    this.price = price;
    this.imageUrl = imageUrl;
    this.category = category;
}

// update()도 동일하게 검증 적용
public void update(String name, int price, String imageUrl, Category category) {
    List<String> errors = ProductNameValidator.validate(name);
    if (!errors.isEmpty()) {
        throw new IllegalArgumentException(String.join(", ", errors));
    }
    this.name = name;
    this.price = price;
    this.imageUrl = imageUrl;
    this.category = category;
}
```

---

**`ProductQueryService.java` (신규)**

```java
package gift.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class ProductQueryService {

    private final ProductRepository productRepository;

    public ProductQueryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product findById(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("상품이 존재하지 않습니다. id=" + id));
    }

    public Page<Product> findAll(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    public List<Product> findAll() {
        return productRepository.findAll();
    }
}
```

---

**`ProductCommandService.java` (신규)**

```java
package gift.product;

import gift.category.Category;
import gift.category.CategoryQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductCommandService {

    private final ProductRepository productRepository;
    private final ProductQueryService productQueryService;
    private final CategoryQueryService categoryQueryService;

    public ProductCommandService(
        ProductRepository productRepository,
        ProductQueryService productQueryService,
        CategoryQueryService categoryQueryService
    ) {
        this.productRepository = productRepository;
        this.productQueryService = productQueryService;
        this.categoryQueryService = categoryQueryService;
    }

    // REST API 경로 — "카카오" 검사 포함
    public Product create(ProductRequest request) {
        validateKakao(request.name());
        return save(request);
    }

    public Product update(Long id, ProductRequest request) {
        validateKakao(request.name());
        return applyUpdate(id, request);
    }

    // Admin 경로 — "카카오" 검사 없음
    public Product createForAdmin(ProductRequest request) {
        return save(request);
    }

    public Product updateForAdmin(Long id, ProductRequest request) {
        return applyUpdate(id, request);
    }

    public void delete(Long id) {
        productRepository.deleteById(id);
    }

    private Product save(ProductRequest request) {
        Category category = categoryQueryService.findById(request.categoryId());
        return productRepository.save(request.toEntity(category));
    }

    private Product applyUpdate(Long id, ProductRequest request) {
        Product product = productQueryService.findById(id);
        Category category = categoryQueryService.findById(request.categoryId());
        product.update(request.name(), request.price(), request.imageUrl(), category);
        return product;
    }

    private void validateKakao(String name) {
        if (name != null && name.contains("카카오")) {
            throw new IllegalArgumentException(
                "\"카카오\"가 포함된 상품명은 담당 MD와 협의한 경우에만 사용할 수 있습니다.");
        }
    }
}
```

---

**`ProductController.java` (수정 — Service 주입)**

```java
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductQueryService productQueryService;
    private final ProductCommandService productCommandService;

    // ProductRepository, CategoryRepository 직접 주입 제거

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getProducts(Pageable pageable) {
        return ResponseEntity.ok(productQueryService.findAll(pageable).map(ProductResponse::from));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(ProductResponse.from(productQueryService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        Product saved = productCommandService.create(request);
        return ResponseEntity.created(URI.create("/api/products/" + saved.getId()))
            .body(ProductResponse.from(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
        @PathVariable Long id,
        @Valid @RequestBody ProductRequest request
    ) {
        return ResponseEntity.ok(ProductResponse.from(productCommandService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productCommandService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
```

---

**`ProductControllerTest.java` (신규 — MockMvc 통합 테스트 골격)**

```java
package gift.product;

import gift.AbstractIntegrationTest;
import gift.category.Category;
import gift.category.CategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ProductControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;

    @Test
    @DisplayName("상품 목록을 페이지로 조회한다")
    void test01() throws Exception { ... }

    @Test
    @DisplayName("단건 조회 시 존재하지 않는 id이면 404를 반환한다")
    void test02() throws Exception { ... }

    @Test
    @DisplayName("유효한 요청으로 상품을 생성하면 201을 반환한다")
    void test03() throws Exception { ... }

    @Test
    @DisplayName("존재하지 않는 카테고리로 생성하면 404를 반환한다")
    void test04() throws Exception { ... }

    @Test
    @DisplayName("이름이 15자를 초과하면 400을 반환한다")
    void test05() throws Exception { ... }

    @Test
    @DisplayName("REST API에서 카카오가 포함된 이름으로 생성하면 400을 반환한다")
    void test06() throws Exception { ... }

    @Test
    @DisplayName("상품을 수정하면 200을 반환한다")
    void test07() throws Exception { ... }

    @Test
    @DisplayName("상품을 삭제하면 204를 반환한다")
    void test08() throws Exception { ... }
}
```

---

**`ProductTest.java` (신규 — 순수 단위 테스트, Spring 컨텍스트 불필요)**

```java
package gift.product;

import gift.category.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    private static final Category DUMMY_CATEGORY =
        new Category("테스트", "#FFFFFF", "http://img.com", null);

    @Test
    @DisplayName("유효한 이름으로 Product를 생성한다")
    void test01() { ... }

    @Test
    @DisplayName("이름이 15자를 초과하면 IllegalArgumentException을 던진다")
    void test02() {
        assertThatThrownBy(() -> new Product("a".repeat(16), 1000, "http://img.com", DUMMY_CATEGORY))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("허용되지 않는 특수문자가 포함되면 IllegalArgumentException을 던진다")
    void test03() { ... }

    @Test
    @DisplayName("이름이 null이면 IllegalArgumentException을 던진다")
    void test04() { ... }
}
```

---

## 5. 주요 고려사항

1. **`orElse(null)` 제거 방식**: 스타일 정리 단계에서는 `orElse(null)` → `orElseThrow()` + GlobalExceptionHandler 위임으로 변경한다. 이후 서비스 추출 단계에서 해당 로직이 서비스로 이동하면 컨트롤러의 null 체크 자체가 사라진다.

2. **`CategoryQueryService.findById()` 신규 추가**: 현재 CategoryQueryService에 없으므로 이번 단계에서 추가한다. Category 도메인의 기존 테스트가 영향받지 않는지 확인한다.

3. **`ProductNameValidator` 잔존 여부**: 포맷 검증 로직을 Product 생성자 내부로 이동하면 `ProductNameValidator`는 `Product` 내부에서만 호출된다. 클래스는 유지하되 직접 외부 호출은 제거한다.

4. **Admin의 "카카오" 허용**: `ProductCommandService`에 `createForAdmin()`·`updateForAdmin()` 메서드를 추가한다. 이 메서드들은 "카카오" 검사를 수행하지 않는다. `AdminProductController`는 이 메서드들을 호출한다. boolean 파라미터(`allowKakao`) 방식은 호출부에서 의미를 읽기 어려우므로 채택하지 않는다.

5. **`@ExceptionHandler(IllegalArgumentException.class)` 중복**: `ProductController`의 지역 `@ExceptionHandler`와 `GlobalExceptionHandler`가 충돌하지 않는지 확인한다. 지역 핸들러가 우선하므로 의도한 동작이 맞는지 검토한다.

---

## 6. 구현 순서 (TDD)

**[스타일 정리 — 구조적 변경, 행동 보존]**

1. [x] `ProductController` `orElse(null)` → `orElseThrow()` 변경 (4곳)

**[통합 테스트 — 현재 동작 Green으로 고정]**

2. [x] `ProductControllerTest` 작성 (test01~test09) — MockMvc, UUID 이름으로 데이터 생성
3. [x] `AdminProductControllerTest` 작성 (test01~test07) — Thymeleaf 뷰, form submit

**[서비스 추출 — 구조적 변경, 행동 보존]**

4. [x] `CategoryQueryService.findById()` 추가 및 기존 테스트 Green 유지 확인
5. [x] `ProductQueryServiceTest` 작성 → `ProductQueryService` TDD 구현
6. [x] `ProductCommandServiceTest` 작성 → `ProductCommandService` TDD 구현
7. [x] `ProductController` 리팩터링 — Service 주입, Repository 의존 제거
8. [x] `AdminProductController` 리팩터링 — Service 주입
9. [x] 전체 테스트 Green 확인 (동작 보존 검증)

**[도메인 책임 이동 — 행동 변경 포함]**

10. [x] `ProductTest` 작성 (test01~test04) — Red (Product 생성자 검증 미존재)
11. [x] `Product` 생성자·`update()` 에 포맷 검증 추가 — Green
12. [x] `ProductCommandService`에서 외부 `validateFormat()` 호출 제거 (`AdminProductController`의 validate 호출은 폼 재출력 목적이므로 유지)
13. [x] 전체 테스트 Green 확인

---

## 7. 인수 조건 (Acceptance Criteria)

- [x] `ProductControllerTest` test01~test09이 모두 Green이다
- [x] `AdminProductControllerTest` test01~test07이 모두 Green이다
- [x] `ProductQueryServiceTest`·`ProductCommandServiceTest`가 모두 Green이다
- [x] `ProductTest` test01~test04가 모두 Green이다 (Spring 컨텍스트 없는 순수 단위 테스트)
- [x] `ProductController`가 Repository를 직접 주입받지 않는다 (`ProductQueryService`·`ProductCommandService`만 주입)
- [x] `AdminProductController`가 Repository를 직접 주입받지 않는다
- [x] `Product` 생성자에서 이름 포맷 검증을 수행한다
- [x] 전체 테스트가 Green이다
