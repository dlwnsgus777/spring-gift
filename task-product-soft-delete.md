# spec: 상품 소프트 삭제 — FK 제약 위반 해결

## 1. 기능 개요

상품을 삭제할 때 `wish.product_id → product.id`, `orders.option_id → options.id` FK 체인으로 인해 실제 DELETE 시 제약 위반이 발생한다.
소프트 삭제(soft delete) 방식으로 `product` 행은 DB에 유지하면서 `deleted = true` 플래그만 세워 모든 조회에서 제외한다.
이렇게 하면 FK 제약 위반 없이 상품을 논리적으로 삭제하고, 주문 이력도 보존할 수 있다.

### 기능 구성

| 항목 | 설명 |
|------|------|
| **상품 소프트 삭제** | `deleted = true` 플래그 설정. DB 행은 유지, Repository 메서드에서 명시적으로 제외 |
| **위시 연계 삭제** | 삭제된 상품의 위시는 `@ManyToOne`으로 연결되어 있어 product가 null처럼 동작할 수 있으므로 함께 제거 |
| **주문 이력 보존** | 주문은 option을 통해 간접 참조. product 행이 남아 FK 위반 없음 → 삭제 불필요 |

---

## 2. API 설계

기존 `DELETE /api/products/{id}` 엔드포인트를 그대로 사용한다. 클라이언트 계약 변경 없음.

---

## 3. 비즈니스 로직

### 3-1. 소프트 삭제 흐름

1. `ProductCommandService.delete(id)` 호출
2. `productQueryService.findById(id)` — 존재하지 않으면 `NoSuchElementException`
3. `product.delete()` — `deleted = true` 플래그 세팅 (Dirty Checking으로 UPDATE 자동 발생)
4. `wishRepository.deleteByProductId(id)` — 연결된 위시 물리 삭제

### 3-2. 조회 필터 — 명시적 Repository 메서드

`@SQLRestriction` 같은 숨겨진 자동 필터 없이, Repository 메서드 이름에 `AndDeletedFalse`를 명시해 의도를 드러낸다.

| 기존 메서드 | 변경 후 메서드 |
|------------|--------------|
| `findById(Long id)` | `findByIdAndDeletedFalse(Long id)` |
| `findAll(Pageable)` | `findAllByDeletedFalse(Pageable)` |
| `findAll()` | `findAllByDeletedFalse()` |
| `existsByCategoryId(Long)` | `existsByCategoryIdAndDeletedFalse(Long)` |

`ProductQueryService`의 각 메서드는 변경된 Repository 메서드를 호출하도록 수정한다.

### 3-3. 위시 처리 이유

`Wish.product`는 `@ManyToOne` JPA 관계다. 소프트 삭제된 product를 가진 Wish를 조회하면 product 필드 접근 시 NPE 위험이 있다.
따라서 소프트 삭제 시 연관 위시를 명시적으로 삭제한다.

---

## 4. 구현 대상 파일

### product 도메인

| 파일 | 역할 |
|------|------|
| `Product.java` | `deleted` 필드 추가, `delete()` 메서드 추가 |
| `ProductRepository.java` | 기존 조회 메서드를 `AndDeletedFalse` 버전으로 추가 |
| `ProductQueryService.java` | 변경된 Repository 메서드 호출로 수정 |
| `ProductCommandService.java` | `delete()` — `deleteById` → `delete` + 위시 삭제 |

### wish 도메인

| 파일 | 역할 |
|------|------|
| `WishRepository.java` | `deleteByProductId(Long productId)` 메서드 추가 |

### DB 마이그레이션

| 파일 | 역할 |
|------|------|
| `V3__Add_deleted_to_product.sql` | `product` 테이블에 `deleted` 컬럼 추가 |

### 테스트

| 파일 | 역할 |
|------|------|
| `ProductCommandServiceTest.java` | `test06` 유지 + `test12` 추가 (위시 연계 삭제 검증) |
| `ProductQueryServiceTest.java` | 소프트 삭제된 상품이 조회 결과에서 제외되는지 검증 테스트 추가 |

### 패키지 위치

```
src/main/java/gift/
├── product/
│   ├── Product.java                 (수정)
│   ├── ProductRepository.java       (수정)
│   ├── ProductQueryService.java     (수정)
│   └── ProductCommandService.java   (수정)
├── wish/
│   └── WishRepository.java          (수정)
src/main/resources/db/migration/
└── V3__Add_deleted_to_product.sql   (신규)
```

### 코드 스니핏

**V3__Add_deleted_to_product.sql**
```sql
ALTER TABLE product ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
```

**Product.java** (추가 부분만)
```java
@Entity
public class Product {

    // 기존 필드들 ...
    private boolean deleted = false;

    public void delete() {
        this.deleted = true;
    }
}
```

**ProductRepository.java**
```java
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByIdAndDeletedFalse(Long id);
    Page<Product> findAllByDeletedFalse(Pageable pageable);
    List<Product> findAllByDeletedFalse();
    boolean existsByCategoryIdAndDeletedFalse(Long categoryId);
}
```

**ProductQueryService.java**
```java
public Product findById(Long id) {
    return productRepository.findByIdAndDeletedFalse(id)
        .orElseThrow(() -> new NoSuchElementException("상품이 존재하지 않습니다. id=" + id));
}

public Page<Product> findAll(Pageable pageable) {
    return productRepository.findAllByDeletedFalse(pageable);
}

public List<Product> findAll() {
    return productRepository.findAllByDeletedFalse();
}
```

**ProductCommandService.java**
```java
public void delete(Long id) {
    Product product = productQueryService.findById(id);
    wishRepository.deleteByProductId(id);
    product.delete();
}
```

**WishRepository.java**
```java
void deleteByProductId(Long productId);
```

**ProductCommandServiceTest.java** (추가 테스트)
```java
@Test
@DisplayName("상품을 삭제하면 조회되지 않는다")
void test06() {
    // arrange
    Product saved = productRepository.save(...);

    // act
    productCommandService.delete(saved.getId());

    // assert — 명시적 AndDeletedFalse 메서드로 조회되지 않음
    assertThat(productRepository.findByIdAndDeletedFalse(saved.getId())).isEmpty();
}

@Test
@DisplayName("상품을 삭제하면 연결된 위시도 함께 삭제된다")
void test12() {
    // arrange
    Product product = ...;
    wishRepository.save(new Wish(memberId, product));

    // act
    productCommandService.delete(product.getId());

    // assert
    assertThat(wishRepository.findByMemberIdAndProductId(memberId, product.getId())).isEmpty();
}
```

---

## 5. 주요 고려사항

1. **기존 `findById` 호출 제거**: `ProductRepository`의 기본 `findById`는 삭제된 상품도 반환한다. `ProductQueryService`를 통해서만 상품을 조회하도록 일관성을 유지한다. (직접 `productRepository.findById()` 호출 금지)

2. **`existsByCategoryId` → `existsByCategoryIdAndDeletedFalse`**: `CategoryCommandService`의 카테고리 삭제 조건 검사도 함께 수정한다. 소프트 삭제된 상품은 카테고리 삭제 조건에서 제외되어야 한다.

3. **기존 test06 수정 필요**: 기존 `test06`은 `productRepository.findById(id)).isEmpty()`를 검증한다. 소프트 삭제 후에도 기본 `findById`는 행을 반환하므로 `findByIdAndDeletedFalse`로 수정해야 한다.

4. **AdminProductController 영향**: 관리자 API도 동일하게 필터링된다. 삭제된 상품 조회가 필요하다면 `findById` (필터 없는 기본 메서드)를 별도 관리자용 메서드로 활용할 수 있다.

---

## 6. 구현 순서 (TDD)

### 구조 변경 (작동 불변)
1. [ ] `V3__Add_deleted_to_product.sql` — `deleted BOOLEAN NOT NULL DEFAULT FALSE` 컬럼 추가
2. [ ] `Product.java` — `deleted` 필드, `delete()` 추가
3. [ ] `ProductRepository.java` — `AndDeletedFalse` 메서드 추가
4. [ ] `ProductQueryService.java` — 새 Repository 메서드 호출로 수정
5. [ ] `WishRepository.java` — `deleteByProductId(Long productId)` 추가
6. [ ] `CategoryCommandService.java` — `existsByCategoryIdAndDeletedFalse` 호출로 수정

### 작동 변경 (테스트로 증명)
7. [ ] `ProductCommandServiceTest.java` — `test06` 수정 + `test12` 추가 (위시 연계 삭제) → Red 확인
8. [ ] `ProductCommandService.delete()` — `deleteById` → `delete` + 위시 삭제 → Green 확인
9. [ ] `ProductQueryServiceTest.java` — 소프트 삭제 후 `findAll()` 결과에서 제외 검증 테스트 추가

---

## 7. 인수 조건 (Acceptance Criteria)

- [ ] 상품 삭제 API 호출 후 `GET /api/products/{id}` 요청 시 404 반환
- [ ] 삭제된 상품의 DB 행은 존재하며 `deleted = true`
- [ ] 소프트 삭제된 상품이 `findAll()` 목록에서 제외된다
- [ ] 소프트 삭제된 상품에 연결된 위시가 제거된다
- [ ] 삭제된 상품에 연결된 주문 이력은 보존된다
- [ ] `ProductCommandServiceTest` 전체 Green
- [ ] `ProductQueryServiceTest` 전체 Green
