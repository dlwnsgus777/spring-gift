# spec: Category 도메인 정리

## 1. 기능 개요

Category 도메인의 통합 테스트를 작성해 현재 API 동작을 Green 테스트로 고정한다.
이후 `orElse(null)` 패턴을 `orElseThrow` + `@ControllerAdvice`로 교체하고,
Controller에서 Repository 의존성을 분리해 `CategoryService`를 추출한다.

### 작업 구성

| 작업 | 설명 | 유형 |
|------|------|------|
| 통합 테스트 작성 | 현재 API 동작을 Green 테스트로 고정 | 테스트 추가 |
| 스타일 정리 | `orElse(null)` → `orElseThrow` + `@ControllerAdvice` | 구조 변경 (동작 동일) |
| Service 추출 | Controller → Service로 비즈니스 로직 이동 | 구조 변경 (동작 동일) |

---

## 2. API 명세 (현행 유지)

> 이 단계에서는 외부 API 스펙 변경 없음. 기존 명세 그대로 동작해야 한다.

| HTTP | 경로 | 성공 응답 | 실패 응답 |
|------|------|----------|----------|
| GET | `/api/categories` | 200 + `List<CategoryResponse>` | — |
| POST | `/api/categories` | 201 + `CategoryResponse` + Location 헤더 | 400 (유효성 실패) |
| PUT | `/api/categories/{id}` | 200 + `CategoryResponse` | 404 (미존재 id) |
| DELETE | `/api/categories/{id}` | 204 | 204 (미존재 id도 동일 — 멱등성) |

---

## 3. 비즈니스 로직

### 3-1. 통합 테스트 설계

`AbstractIntegrationTest`에 `@AutoConfigureMockMvc`를 추가해 MockMvc를 주입받아 사용한다.

| 테스트 메서드 | DisplayName | 검증 항목 |
|-------------|-------------|---------|
| `test01` | 전체 카테고리 목록을 조회한다 | 200, 응답 배열 반환 |
| `test02` | 카테고리를 생성하면 201과 Location 헤더를 반환한다 | 201, Location 헤더, 응답 body id |
| `test03` | name이 빈 값이면 카테고리 생성에 실패한다 | 400 |
| `test04` | 존재하는 카테고리를 수정하면 변경된 내용을 반환한다 | 200, 변경된 name 반환 |
| `test05` | 존재하지 않는 카테고리를 수정하면 404를 반환한다 | 404 |
| `test06` | 카테고리를 삭제하면 204를 반환한다 | 204 |
| `test07` | 존재하지 않는 카테고리를 삭제해도 204를 반환한다 | 204 (멱등성 유지) |

### 3-2. Optional 패턴 교체

`updateCategory` 메서드의 `orElse(null)` + null 체크를 `orElseThrow`로 교체한다.
`NoSuchElementException`을 `@ControllerAdvice`에서 잡아 404로 변환한다.

```
before: findById(id).orElse(null) → null check → return notFound
after:  findById(id).orElseThrow() → NoSuchElementException → GlobalExceptionHandler → 404
```

### 3-3. Service 추출 범위

Controller는 HTTP 입출력만 담당하고, 모든 비즈니스 로직은 Service로 이동한다.

| Controller 역할 | Service 역할 |
|----------------|-------------|
| `@Valid` 요청 수신 | Repository 호출 |
| `ResponseEntity` 반환 | 도메인 객체 조작 (`category.update(...)`) |
| Location URI 생성 | `CategoryResponse` 변환 |

---

## 4. 구현 대상 파일

### gift/category 패키지

| 파일 | 상태 | 역할 |
|------|------|------|
| `CategoryController.java` | 수정 | Repository 의존성 제거, Service 호출로 변경 |
| `CategoryService.java` | **신규** | 비즈니스 로직 집약 |

### gift/common 패키지

| 파일 | 상태 | 역할 |
|------|------|------|
| `GlobalExceptionHandler.java` | **신규** | `NoSuchElementException` → 404 변환 |

### 테스트

| 파일 | 상태 | 역할 |
|------|------|------|
| `AbstractIntegrationTest.java` | 수정 | `@AutoConfigureMockMvc` 추가 |
| `CategoryControllerTest.java` | **신규** | Category API 통합 테스트 |

### 패키지 위치

```
src/
├── main/java/gift/
│   ├── category/
│   │   ├── Category.java               (변경 없음)
│   │   ├── CategoryController.java     (수정 — Service 위임)
│   │   ├── CategoryRepository.java     (변경 없음)
│   │   ├── CategoryRequest.java        (변경 없음)
│   │   ├── CategoryResponse.java       (변경 없음)
│   │   └── CategoryService.java        (신규)
│   └── common/
│       └── GlobalExceptionHandler.java (신규)
└── test/java/gift/
    ├── AbstractIntegrationTest.java    (수정 — @AutoConfigureMockMvc)
    └── category/
        └── CategoryControllerTest.java (신규)
```

### 코드 스니핏

#### CategoryService.java (신규)

```java
@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll().stream()
            .map(CategoryResponse::from)
            .toList();
    }

    public CategoryResponse create(CategoryRequest request) {
        Category saved = categoryRepository.save(request.toEntity());
        return CategoryResponse.from(saved);
    }

    public CategoryResponse update(Long id, CategoryRequest request) {
        // orElseThrow() → NoSuchElementException → GlobalExceptionHandler → 404
        Category category = categoryRepository.findById(id).orElseThrow();
        category.update(request.name(), request.color(), request.imageUrl(), request.description());
        categoryRepository.save(category);
        return CategoryResponse.from(category);
    }

    public void delete(Long id) {
        categoryRepository.deleteById(id);
    }
}
```

#### CategoryController.java (수정 후)

```java
@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getCategories() {
        return ResponseEntity.ok(categoryService.findAll());
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse response = categoryService.create(request);
        return ResponseEntity.created(URI.create("/api/categories/" + response.id()))
            .body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(
        @PathVariable Long id,
        @Valid @RequestBody CategoryRequest request
    ) {
        return ResponseEntity.ok(categoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

#### GlobalExceptionHandler.java (신규)

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Void> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.notFound().build();
    }
}
```

#### AbstractIntegrationTest.java (수정)

```java
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc  // 추가
public abstract class AbstractIntegrationTest {
    // ... 기존 컨테이너 설정 유지
}
```

#### CategoryControllerTest.java (신규)

```java
class CategoryControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        categoryRepository.deleteAll();
    }

    @Test
    @DisplayName("전체 카테고리 목록을 조회한다")
    void test01() throws Exception {
        // arrange
        // act
        // assert
        mockMvc.perform(get("/api/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("카테고리를 생성하면 201과 Location 헤더를 반환한다")
    void test02() throws Exception {
        // arrange
        CategoryRequest request = new CategoryRequest("테스트", "#FFFFFF", "http://img.url", null);

        // act & assert
        mockMvc.perform(post("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.name").value("테스트"));
    }

    @Test
    @DisplayName("name이 빈 값이면 카테고리 생성에 실패한다")
    void test03() throws Exception {
        // arrange
        CategoryRequest request = new CategoryRequest("", "#FFFFFF", "http://img.url", null);

        // act & assert
        mockMvc.perform(post("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("존재하는 카테고리를 수정하면 변경된 내용을 반환한다")
    void test04() throws Exception {
        // arrange
        Category saved = categoryRepository.save(new Category("원래이름", "#000000", "http://img.url", null));
        CategoryRequest request = new CategoryRequest("바뀐이름", "#FFFFFF", "http://new.url", null);

        // act & assert
        mockMvc.perform(put("/api/categories/" + saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("바뀐이름"));
    }

    @Test
    @DisplayName("존재하지 않는 카테고리를 수정하면 404를 반환한다")
    void test05() throws Exception {
        // arrange
        CategoryRequest request = new CategoryRequest("이름", "#FFFFFF", "http://img.url", null);

        // act & assert
        mockMvc.perform(put("/api/categories/999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("카테고리를 삭제하면 204를 반환한다")
    void test06() throws Exception {
        // arrange
        Category saved = categoryRepository.save(new Category("삭제대상", "#000000", "http://img.url", null));

        // act & assert
        mockMvc.perform(delete("/api/categories/" + saved.getId()))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("존재하지 않는 카테고리를 삭제해도 204를 반환한다")
    void test07() throws Exception {
        // arrange (nothing to set up — id does not exist)

        // act & assert
        mockMvc.perform(delete("/api/categories/999999"))
            .andExpect(status().isNoContent());
    }
}
```

---

## 5. 주요 고려사항

1. **`@AutoConfigureMockMvc` 위치**: `AbstractIntegrationTest`에 추가하면 모든 하위 테스트 클래스에 자동 적용된다. 향후 다른 도메인 테스트도 MockMvc를 바로 `@Autowired`로 주입받을 수 있다.

2. **`GlobalExceptionHandler` 위치**: `gift.common` 패키지 신규 생성. 향후 다른 도메인에서도 재사용하므로 category 패키지 밖에 위치시킨다.

3. **`@BeforeEach` 데이터 초기화**: Flyway V2 기본 데이터(id=1,2,3)가 테스트 간 간섭할 수 있다. `categoryRepository.deleteAll()` 또는 `@Transactional` + `@Rollback`으로 격리한다.

4. **삭제 멱등성**: `deleteById`는 존재하지 않는 id에도 예외 없이 성공한다 (Spring Data JPA 기본 동작). 현재 동작을 그대로 테스트로 고정한다.

---

## 6. 구현 순서

> **원칙**: 테스트 Green 고정 → 구조 변경 순. 각 단계 완료 후 테스트 Green 확인.

### 1단계: 테스트 인프라 준비 (구조 변경)

가- [x] `AbstractIntegrationTest`에 `@AutoConfigureMockMvc` 추가

### 2단계: 통합 테스트 작성 (행동 고정)

- [x] `CategoryControllerTest` 클래스 생성 (AbstractIntegrationTest 상속)
- [x] `test01` — GET /api/categories → 200
- [x] `test02` — POST /api/categories (유효한 요청) → 201 + Location
- [x] `test03` — POST /api/categories (name 빈 값) → 400
- [x] `test04` — PUT /api/categories/{id} (존재하는 id) → 200
- [x] `test05` — PUT /api/categories/{id} (미존재 id) → 404
- [x] `test06` — DELETE /api/categories/{id} (존재하는 id) → 204
- [x] `test07` — DELETE /api/categories/{id} (미존재 id) → 204
- [x] 전체 테스트 Green 확인

### 3단계: 스타일 정리 (구조 변경 — 동작 동일)

- [x] `GlobalExceptionHandler` 생성 (`NoSuchElementException` → 404)
- [x] `CategoryController.updateCategory`의 `orElse(null)` → `orElseThrow()` 교체
- [x] null 체크 분기 제거
- [x] 통합 테스트 Green 유지 확인

### 4단계: Service 추출 (구조 변경 — 동작 동일)

- [ ] `CategoryService` 클래스 생성
- [ ] Controller의 `findAll`, `create`, `update`, `delete` 로직을 Service로 이동
- [ ] Controller가 Repository 대신 Service를 주입받도록 변경
- [ ] 통합 테스트 Green 유지 확인

---

## 7. 인수 조건

- [ ] `CategoryControllerTest` 7개 테스트 모두 Green
- [ ] `CategoryController`가 `CategoryRepository`를 직접 의존하지 않음
- [ ] `updateCategory`에 `orElse(null)` + null 체크 분기 없음
- [ ] `GlobalExceptionHandler`가 `NoSuchElementException`을 404로 변환
- [ ] 전체 테스트 (`./gradlew test`) Green 유지
