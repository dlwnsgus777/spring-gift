# spec: 카테고리 삭제 시 연결 상품 존재 여부 검증 (409 처리)

## 1. 기능 개요

현재 `DELETE /api/categories/{id}` 호출 시 해당 카테고리에 연결된 상품이 존재하면 DB FK 제약 위반으로 `DataIntegrityViolationException`이 발생하고 500이 반환된다.  
서비스 레이어에서 선제 검증 후 도메인 예외(`CategoryHasProductsException`)를 던져 409 Conflict를 반환하도록 수정한다.

### 기능 구성

| 섹션 / 기능 | 설명 |
|------------|------|
| **연결 상품 선제 검증** | 삭제 전 `ProductRepository.existsByCategoryId()` 호출 |
| **도메인 예외 클래스** | `CategoryHasProductsException` 신규 추가 |
| **GlobalExceptionHandler 등록** | 새 예외를 409로 변환하는 핸들러 메서드 추가 |
| **테스트** | 서비스 단위 테스트 + 컨트롤러 통합 테스트 시나리오 추가 |

---

## 2. API 설계

### API - 카테고리 삭제

```
DELETE /api/categories/{id}
```

- 카테고리를 삭제한다. 연결된 상품이 있으면 삭제를 거부한다.

**Response 204 No Content** — 삭제 성공

**Response 409 Conflict** — 연결된 상품이 존재하여 삭제 불가

```
HTTP 409 Conflict
Body: "해당 카테고리에 연결된 상품이 존재합니다. id={id}"
```

**변경 없는 케이스**

| 케이스 | 기존 | 변경 후 |
|--------|------|---------|
| 연결 상품 없음 | 204 | 204 (동일) |
| 연결 상품 있음 | 500 | **409** |
| 존재하지 않는 ID | 204 (deleteById 통과) | 204 (이번 범위 제외) |

---

## 3. 비즈니스 로직

### 3-1. 카테고리 삭제 흐름

1. `ProductRepository.existsByCategoryId(id)` 호출
2. 결과가 `true`이면 `CategoryHasProductsException` 던짐
3. `false`이면 `categoryRepository.deleteById(id)` 실행

### 3-2. 예외 → HTTP 상태 매핑

| 예외 클래스 | HTTP 상태 | 응답 바디 |
|-------------|----------|-----------|
| `CategoryHasProductsException` | 409 Conflict | 예외 메시지 문자열 |

---

## 4. 구현 대상 파일

### category 모듈

| 파일 | 역할 |
|------|------|
| `CategoryHasProductsException` | 신규 — 카테고리에 연결 상품이 있을 때 던지는 도메인 예외 |
| `CategoryCommandService` | 수정 — `delete()` 메서드에 선제 검증 추가 |

### product 모듈

| 파일 | 역할 |
|------|------|
| `ProductRepository` | 수정 — `existsByCategoryId(Long categoryId)` 메서드 추가 |

### common 모듈

| 파일 | 역할 |
|------|------|
| `GlobalExceptionHandler` | 수정 — `CategoryHasProductsException` 핸들러 추가 |

### 패키지 위치

```
src/main/java/gift/
├── category/
│   ├── CategoryHasProductsException.java   (신규)
│   └── CategoryCommandService.java         (수정)
├── product/
│   └── ProductRepository.java              (수정)
└── common/
    └── GlobalExceptionHandler.java         (수정)

src/test/java/gift/
├── category/
│   ├── CategoryCommandServiceTest.java     (수정 — test05 추가)
│   └── CategoryControllerTest.java         (수정 — test08 추가)
```

### 코드 스니핏

**CategoryHasProductsException.java** (신규)
```java
package gift.category;

public class CategoryHasProductsException extends RuntimeException {
    public CategoryHasProductsException(String message) {
        super(message);
    }
}
```

**ProductRepository.java** (메서드 추가)
```java
public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsByCategoryId(Long categoryId);
}
```

**CategoryCommandService.delete()** (수정)
```java
public void delete(Long id) {
    if (productRepository.existsByCategoryId(id)) {
        throw new CategoryHasProductsException(
            "해당 카테고리에 연결된 상품이 존재합니다. id=" + id
        );
    }
    categoryRepository.deleteById(id);
}
```

> `CategoryCommandService`에 `ProductRepository` 의존성 주입 필요

**GlobalExceptionHandler** (핸들러 추가)
```java
@ExceptionHandler(CategoryHasProductsException.class)
public ResponseEntity<String> handleCategoryHasProducts(CategoryHasProductsException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
}
```

**CategoryCommandServiceTest** (test05 추가)
```java
@Test
@DisplayName("연결된 상품이 있는 카테고리를 삭제하면 CategoryHasProductsException을 던진다")
void test05() {
    // arrange — Flyway V2 시드 데이터: 카테고리에 상품이 연결돼 있음
    Long categoryIdWithProducts = categoryRepository.findAll().get(0).getId();

    // act & assert
    assertThatThrownBy(() -> categoryCommandService.delete(categoryIdWithProducts))
        .isInstanceOf(CategoryHasProductsException.class);
}
```

**CategoryControllerTest** (test08 추가)
```java
@Test
@DisplayName("연결된 상품이 있는 카테고리를 삭제하면 409를 반환한다")
void test08() throws Exception {
    // arrange — Flyway V2 시드 데이터: 첫 번째 카테고리는 상품과 연결됨
    Long categoryIdWithProducts = categoryRepository.findAll().get(0).getId();

    // act & assert
    mockMvc.perform(delete("/api/categories/" + categoryIdWithProducts))
        .andExpect(status().isConflict());
}
```

---

## 5. 주요 고려사항

1. **`CategoryCommandService`에 `ProductRepository` 주입**: category 모듈이 product 모듈의 Repository에 의존하게 된다. 현재 코드베이스에서 단방향 의존이 생기는 것이 맞는지 확인 필요.
   - 대안: `ProductQueryService`를 통해 간접 조회 (레이어 규칙 준수)

2. **Flyway 시드 데이터 활용**: `test05`, `test08` 모두 V2 시드 데이터의 첫 번째 카테고리가 상품과 연결돼 있다는 전제에 의존. 시드 데이터 변경 시 테스트가 깨질 수 있음.

---

## 6. 구현 순서 (TDD)

### Red 단계

1. - [ ] `CategoryCommandServiceTest.test05` 작성 — `CategoryHasProductsException` 기대 (컴파일 에러 = Red)
2. - [ ] `CategoryControllerTest.test08` 작성 — 409 기대 (컴파일 에러 = Red)

### Green 단계

3. - [ ] `CategoryHasProductsException` 신규 생성
4. - [ ] `ProductRepository`에 `existsByCategoryId()` 추가
5. - [ ] `CategoryCommandService.delete()`에 선제 검증 로직 추가
6. - [ ] `GlobalExceptionHandler`에 409 핸들러 추가

### Refactor 단계

7. - [ ] 테스트 재실행 후 Green 확인

---

## 7. 인수 조건 (Acceptance Criteria)

- [ ] 연결된 상품이 있는 카테고리에 `DELETE /api/categories/{id}` 요청 시 409 반환
- [ ] 응답 바디에 사람이 읽을 수 있는 에러 메시지 포함
- [ ] 연결된 상품이 없는 카테고리 삭제 시 204 정상 동작 (기존 테스트 `test04`, `test06` 통과)
- [ ] `CategoryCommandServiceTest.test05` 통과
- [ ] `CategoryControllerTest.test08` 통과
