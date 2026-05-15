# spec: Option 도메인 6단계 — Service 추출 + 생성자 검증 이동

## 1. 기능 개요

Option 도메인을 Product 5단계 패턴과 동일하게 정리한다.
현재 `OptionController`가 `OptionRepository`·`ProductRepository`를 직접 사용하고 있으며, 이름 검증 로직이 컨트롤러에 위치한다.
통합 테스트로 현재 동작을 고정한 뒤, `OptionQueryService` / `OptionCommandService`로 비즈니스 로직을 이동하고, `OptionNameValidator.validate()`를 `Option` 생성자 안으로 옮겨 잘못된 이름의 Option 객체가 생성 자체를 막도록 한다.

### 작업 구성

| 작업 | 유형 | 설명 |
|------|------|------|
| 통합 테스트 작성 | 행동 고정 | 현재 API 동작을 Green 테스트로 잠금 |
| 스타일 정리 | 구조 변경 | `orElse(null)` → Optional, `Collectors.toList()` → `.toList()` |
| OptionQueryService 추출 | 구조 변경 | 조회 로직 이동, 행동 변경 없음 |
| OptionCommandService 추출 | 구조 변경 | 생성·삭제 로직 이동 |
| Option.validateDeletion() 추가 | 도메인 책임 회수 | '최소 1개' 검증을 Option 객체 안으로 이동 |
| Option 생성자 검증 이동 | 도메인 책임 회수 | `OptionNameValidator.validate()` → `Option` 생성자 |
| Option 단위 테스트 | 행동 고정 | Entity 레벨 불변식 검증 |

---

## 2. API 설계

API 스펙 변경 없음 — 기존 3개 엔드포인트를 유지하면서 내부 구현만 변경한다.

| HTTP | 경로 | 동작 | 성공 응답 |
|------|------|------|----------|
| GET | `/api/products/{productId}/options` | 상품 옵션 목록 조회 | 200 |
| POST | `/api/products/{productId}/options` | 옵션 생성 | 201 + Location |
| DELETE | `/api/products/{productId}/options/{optionId}` | 옵션 삭제 | 204 |

---

## 3. 비즈니스 로직

### 3-1. OptionQueryService — 조회

- `findByProductId(Long productId)`: 상품에 속한 옵션 목록을 반환한다.
- `ProductQueryService.findById()`로 상품 존재를 먼저 확인한다. 없으면 `NoSuchElementException` → 404.

### 3-2. OptionCommandService — 생성

1. `ProductQueryService.findById(productId)` — 상품 존재 확인 (없으면 `NoSuchElementException`)
2. `optionRepository.existsByProductIdAndName(productId, name)` — 이름 중복 확인 (중복 시 `IllegalArgumentException`)
3. `new Option(product, name, quantity)` — 생성자 내부에서 이름 규칙 검증 (위반 시 `IllegalArgumentException`)
4. 저장 후 `Option` 반환

### 3-3. OptionCommandService — 삭제

1. `ProductQueryService.findById(productId)` — 상품 존재 확인
2. `optionRepository.findByProductId(productId)`로 해당 상품의 옵션 목록 조회
3. `optionRepository.findById(optionId)` — 옵션 존재 + 상품 소속 확인 (없으면 `NoSuchElementException`)
4. `option.validateDeletion(options.size())` — 최소 1개 규칙을 Option 객체가 직접 판단 (위반 시 `IllegalArgumentException`)
5. 삭제

### 3-4. Option 생성자 검증 이동

`Option` 생성자에서 `OptionNameValidator.validate(name)`을 호출한다. 오류가 있으면 즉시 `IllegalArgumentException`을 던진다. `OptionNameValidator` 클래스는 삭제하지 않고 내부에서 위임 호출한다 (Product 패턴 동일).

```
// before: 컨트롤러에서 검증 후 생성
List<String> errors = OptionNameValidator.validate(name);
if (!errors.isEmpty()) throw ...;
new Option(product, name, quantity);

// after: 생성자에서 불변식 보장
new Option(product, name, quantity); // 내부에서 검증, 위반 시 예외
```

---

## 4. 구현 대상 파일

### option 도메인

| 파일 | 변경 유형 | 역할 |
|------|----------|------|
| `Option.java` | 수정 | 생성자 이름 검증 + `validateDeletion()` 추가 |
| `OptionController.java` | 수정 | Repository 직접 의존 제거, Service 호출로 대체. 스타일 정리 |
| `OptionQueryService.java` | 신규 | `findByProductId()` — `@Transactional(readOnly = true)` |
| `OptionCommandService.java` | 신규 | `create()`, `delete()` — `@Transactional` |
| `OptionControllerTest.java` | 신규 | MockMvc 통합 테스트 |
| `OptionCommandServiceTest.java` | 신규 | CommandService 단위 테스트 (`@Transactional`) |
| `OptionQueryServiceTest.java` | 신규 | QueryService 단위 테스트 (`@Transactional`) |
| `OptionTest.java` | 신규 | Option 엔티티 불변식 단위 테스트 |

### 패키지 위치

```
src/main/java/gift/option/
├── Option.java                  (수정 — 생성자 검증 추가)
├── OptionController.java        (수정 — Service 의존으로 변경, 스타일 정리)
├── OptionQueryService.java      (신규)
├── OptionCommandService.java    (신규)
├── OptionNameValidator.java     (유지 — Option 생성자에서 위임 호출)
├── OptionRepository.java        (유지)
├── OptionRequest.java           (유지)
└── OptionResponse.java          (유지)

src/test/java/gift/option/
├── OptionControllerTest.java    (신규)
├── OptionCommandServiceTest.java (신규)
├── OptionQueryServiceTest.java  (신규)
└── OptionTest.java              (신규)
```

### 코드 스니핏

**Option.java** — 생성자 검증 추가 + validateDeletion() 추가

```java
public Option(Product product, String name, int quantity) {
    validateName(name);          // 이름 규칙 위반 시 IllegalArgumentException
    this.product = product;
    this.name = name;
    this.quantity = quantity;
}

public void validateDeletion(int totalOptionsCount) {
    // 상품의 마지막 옵션은 삭제할 수 없다는 불변식
    if (totalOptionsCount <= 1) {
        throw new IllegalArgumentException("옵션이 1개인 상품은 옵션을 삭제할 수 없습니다.");
    }
}

private static void validateName(String name) {
    List<String> errors = OptionNameValidator.validate(name);
    if (!errors.isEmpty()) {
        throw new IllegalArgumentException(String.join(", ", errors));
    }
}
```

**OptionQueryService.java**

```java
@Service
@Transactional(readOnly = true)
public class OptionQueryService {

    private final OptionRepository optionRepository;
    private final ProductQueryService productQueryService;

    public OptionQueryService(OptionRepository optionRepository, ProductQueryService productQueryService) { ... }

    public List<Option> findByProductId(Long productId) {
        productQueryService.findById(productId); // 상품 존재 확인 — 없으면 NoSuchElementException
        return optionRepository.findByProductId(productId);
    }
}
```

**OptionCommandService.java**

```java
@Service
@Transactional
public class OptionCommandService {

    private final OptionRepository optionRepository;
    private final ProductQueryService productQueryService;

    public OptionCommandService(OptionRepository optionRepository, ProductQueryService productQueryService) { ... }

    public Option create(Long productId, OptionRequest request) {
        Product product = productQueryService.findById(productId);
        if (optionRepository.existsByProductIdAndName(productId, request.name())) {
            throw new IllegalArgumentException("이미 존재하는 옵션명입니다.");
        }
        return optionRepository.save(new Option(product, request.name(), request.quantity()));
    }

    public void delete(Long productId, Long optionId) {
        productQueryService.findById(productId);
        List<Option> options = optionRepository.findByProductId(productId);
        Option option = optionRepository.findById(optionId)
            .filter(o -> o.getProduct().getId().equals(productId))
            .orElseThrow(() -> new NoSuchElementException("옵션이 존재하지 않습니다. id=" + optionId));
        option.validateDeletion(options.size()); // '최소 1개' 규칙을 Option이 판단
        optionRepository.delete(option);
    }
}
```

**OptionController.java** — Service 의존으로 변경 후

```java
@RestController
@RequestMapping(path = "/api/products/{productId}/options")
public class OptionController {
    private final OptionQueryService optionQueryService;
    private final OptionCommandService optionCommandService;

    @GetMapping
    public ResponseEntity<List<OptionResponse>> getOptions(@PathVariable Long productId) {
        List<OptionResponse> options = optionQueryService.findByProductId(productId).stream()
            .map(OptionResponse::from)
            .toList();   // Collectors.toList() → .toList()
        return ResponseEntity.ok(options);
    }

    @PostMapping
    public ResponseEntity<OptionResponse> createOption(@PathVariable Long productId, @Valid @RequestBody OptionRequest request) {
        Option saved = optionCommandService.create(productId, request);
        URI location = URI.create("/api/products/" + productId + "/options/" + saved.getId());
        return ResponseEntity.created(location).body(OptionResponse.from(saved));
    }

    @DeleteMapping("/{optionId}")
    public ResponseEntity<Void> deleteOption(@PathVariable Long productId, @PathVariable Long optionId) {
        optionCommandService.delete(productId, optionId);
        return ResponseEntity.noContent().build();
    }
}
```

**OptionControllerTest.java** (통합 테스트 스케치)

```java
class OptionControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired OptionRepository optionRepository;

    @Test
    @DisplayName("상품의 옵션 목록을 조회하면 200을 반환한다")
    void test01() throws Exception { ... }

    @Test
    @DisplayName("존재하지 않는 상품의 옵션을 조회하면 404를 반환한다")
    void test02() throws Exception { ... }

    @Test
    @DisplayName("유효한 요청으로 옵션을 생성하면 201과 Location 헤더를 반환한다")
    void test03() throws Exception { ... }

    @Test
    @DisplayName("같은 상품에 중복된 이름으로 옵션을 생성하면 400을 반환한다")
    void test04() throws Exception { ... }

    @Test
    @DisplayName("허용되지 않는 특수문자가 포함된 이름으로 생성하면 400을 반환한다")
    void test05() throws Exception { ... }

    @Test
    @DisplayName("옵션을 삭제하면 204를 반환한다")
    void test06() throws Exception { ... }

    @Test
    @DisplayName("상품의 마지막 옵션을 삭제하면 400을 반환한다")
    void test07() throws Exception { ... }

    @Test
    @DisplayName("존재하지 않는 옵션을 삭제하면 404를 반환한다")
    void test08() throws Exception { ... }
}
```

**OptionTest.java** (Option 엔티티 단위 테스트 스케치)

```java
class OptionTest {
    private static final Product DUMMY_PRODUCT = ...; // 테스트용 Product

    @Test
    @DisplayName("유효한 이름으로 Option을 생성한다")
    void test01() { ... }

    @Test
    @DisplayName("이름이 50자를 초과하면 IllegalArgumentException을 던진다")
    void test02() { ... }

    @Test
    @DisplayName("허용되지 않는 특수문자가 포함되면 IllegalArgumentException을 던진다")
    void test03() { ... }

    @Test
    @DisplayName("차감 수량이 현재 재고보다 크면 IllegalArgumentException을 던진다")
    void test04() { ... }

    @Test
    @DisplayName("유효한 수량을 차감하면 재고가 줄어든다")
    void test05() { ... }

    @Test
    @DisplayName("옵션이 1개뿐일 때 validateDeletion을 호출하면 IllegalArgumentException을 던진다")
    void test06() { ... }

    @Test
    @DisplayName("옵션이 2개 이상일 때 validateDeletion은 예외를 던지지 않는다")
    void test07() { ... }
}
```

---

## 5. 주요 고려사항

1. **GlobalExceptionHandler 활용**: `OptionController`의 `@ExceptionHandler(IllegalArgumentException.class)` 로컬 핸들러가 현재 존재한다. `GlobalExceptionHandler`가 이미 같은 역할을 하므로 Service 추출 후 로컬 핸들러를 제거한다.

2. **스타일 정리 대상 (OptionController)**:
   - `orElse(null)` + null 체크 4곳 → Optional 패턴 (`orElseThrow()`)
   - `Collectors.toList()` → `.toList()`
   - `validateName()` private 메서드 → 삭제 (Option 생성자로 이동 후 불필요)

3. **DUMMY_PRODUCT 생성 방법 (OptionTest)**: `Option` 단위 테스트에서 Product가 필요하나 DB가 없다. `Product` 생성자를 직접 호출해 더미 객체를 만들거나, `Mockito.mock(Product.class)`를 사용한다. Product 도메인 테스트(`ProductTest`)에서 `new Category(...)` 더미를 사용한 것과 동일 패턴으로 `new Product(...)`를 직접 호출한다.

---

## 6. 구현 순서 (TDD)

### Phase 1 — 행동 고정 (현재 동작을 테스트로 잠금)

1. [x] `OptionControllerTest` 작성 — 8개 시나리오 (test01~test08) 모두 Green 확인

### Phase 2 — 스타일 정리 (구조 변경, 행동 변경 없음)

2. [x] `OptionController` 스타일 정리 — `orElse(null)` → Optional, `Collectors.toList()` → `.toList()`
3. [x] 테스트 Green 재확인

### Phase 3 — Service 추출 (구조 변경, 행동 변경 없음)

4. [x] `OptionQueryServiceTest` 작성 — `findByProductId()` 시나리오 (Red)
5. [x] `OptionQueryService` 구현 — 테스트 Green
6. [x] `OptionController`에서 조회 로직 → `OptionQueryService`로 이전
7. [x] 통합 테스트 Green 재확인
8. [x] `OptionCommandServiceTest` 작성 — create/delete 시나리오 (Red)
9. [x] `OptionCommandService` 구현 — 테스트 Green
10. [x] `OptionController`에서 생성·삭제 로직 → `OptionCommandService`로 이전
11. [x] 통합 테스트 Green 재확인
12. [x] `OptionController` 로컬 `@ExceptionHandler` 제거

### Phase 4 — 도메인 책임 회수 (검증 위치 이동)

13. [x] `OptionTest` 작성 — 이름 불변식 시나리오 (Red, 생성자 검증 없으므로 실패)
14. [x] `Option` 생성자에 `validateName()` 추가 — 이름 불변식 테스트 Green
15. [x] `OptionCommandService`에서 `validateName()` 호출 제거 (생성자에서 처리하므로 중복)
16. [x] `OptionTest`에 `validateDeletion()` 시나리오 추가 (Red)
17. [x] `Option`에 `validateDeletion(int totalOptionsCount)` 추가 — 테스트 Green
18. [x] `OptionCommandService.delete()`의 인라인 검증 → `option.validateDeletion(options.size())` 호출로 교체
19. [x] 모든 테스트 Green 재확인

---

## 7. 인수 조건 (Acceptance Criteria)

- [x] `OptionControllerTest` 8개 테스트 Green
- [x] `OptionQueryServiceTest` 전체 Green
- [x] `OptionCommandServiceTest` 전체 Green
- [x] `OptionTest` (Entity 불변식) 전체 Green
- [x] `OptionController`가 `OptionRepository`·`ProductRepository`를 직접 의존하지 않음
- [x] `Option` 생성자에서 이름 검증이 수행됨 — 잘못된 이름으로 `new Option(...)` 시 `IllegalArgumentException`
- [x] `Option.validateDeletion()`에서 '최소 1개' 규칙을 판단함 — `OptionCommandService`에 인라인 검증 없음
- [x] `./gradlew test` 전체 Green (기존 테스트 회귀 없음)
