# spec: Wish 도메인 7단계 — 통합 테스트 + 스타일 정리 + 서비스 추출

## 1. 기능 개요

Wish 도메인의 기존 Controller 동작을 통합 테스트로 고정한 뒤, 스타일을 정리하고 비즈니스 로직을 Service 계층으로 분리한다.
인증 추출 책임은 `AuthService.extractMember()`로 이관해 Controller의 null 체크 분기를 제거한다.
완료 후 WishController는 HTTP 변환만 담당하고, 인증·비즈니스 로직은 각 Service가 보유한다.

### 기능 구성

| 작업 | 설명 |
|------|------|
| **통합 테스트** | 현재 API 동작을 MockMvc 테스트로 고정 |
| **스타일 정리** | 흐름 주석 제거, `var` → 타입 명시, `orElse(null)` → Optional |
| **AuthService 확장** | `extractMember(String authorization)` 추가 — 실패 시 예외 |
| **WishQueryService** | 회원별 위시리스트 페이지 조회 |
| **WishCommandService** | 위시 추가(멱등) + 삭제(소유권 검증) |
| **WishController 교체** | Repository 직접 의존 제거, Service 위임 |

---

## 2. API 설계

### API 1 - 위시리스트 조회

```
GET /api/wishes
Authorization: Bearer {token}
```

**Response 200 OK**
```json
{
  "content": [
    {
      "id": 1,
      "productId": 1,
      "name": "맥북 프로 16인치",
      "price": 3360000,
      "imageUrl": "https://example.com/images/macbook.jpg"
    }
  ],
  "totalElements": 1,
  "totalPages": 1
}
```

**엣지 케이스 — 토큰 없음 / 유효하지 않음**
```
401 Unauthorized
```

---

### API 2 - 위시 추가

```
POST /api/wishes
Authorization: Bearer {token}
Content-Type: application/json
```

**Request**
```json
{ "productId": 1 }
```

**Response 201 Created (신규)**
```
Location: /api/wishes/{id}
```
```json
{ "id": 1, "productId": 1, "name": "맥북 프로 16인치", "price": 3360000, "imageUrl": "..." }
```

**Response 200 OK (중복 — 멱등)**
```json
{ "id": 1, "productId": 1, "name": "맥북 프로 16인치", "price": 3360000, "imageUrl": "..." }
```

**엣지 케이스**

| 상황 | 응답 |
|------|------|
| `productId` null | 400 Bad Request |
| 존재하지 않는 productId | 404 Not Found |
| 토큰 없음 / 유효하지 않음 | 401 Unauthorized |

---

### API 3 - 위시 삭제

```
DELETE /api/wishes/{id}
Authorization: Bearer {token}
```

**Response 204 No Content (성공)**

**엣지 케이스**

| 상황 | 응답 |
|------|------|
| 존재하지 않는 위시 ID | 404 Not Found |
| 다른 회원 소유의 위시 | 403 Forbidden |
| 토큰 없음 / 유효하지 않음 | 401 Unauthorized |

---

## 3. 비즈니스 로직

### 3-1. 인증 추출 (`AuthService.extractMember`)

`Authorization` 헤더에서 `Bearer` 접두어를 제거하고 `JwtProvider.getEmail()`로 이메일을 추출한다.
`MemberQueryService.findByEmail()`로 회원을 조회한다. 토큰이 없거나 파싱에 실패하거나 회원을 찾지 못하면 `IllegalArgumentException`을 던진다.
`GlobalExceptionHandler`가 이를 400으로 변환하므로, 401이 필요하다면 별도 예외 타입을 추가한다.

> **참고**: 현재 `AuthenticationResolver.extractMember()`는 실패 시 `null`을 반환한다. `AuthService.extractMember()`는 예외를 던지는 방식으로 설계해 Controller의 null 체크 분기를 제거한다.

### 3-2. 위시 추가 (`WishCommandService.addWish`)

동일 회원 + 동일 상품 위시가 이미 존재하면 새로 생성하지 않고 기존 항목을 반환한다(멱등).
신규 추가 시 `Wish`를 저장하고 반환한다. 상품 존재 여부는 `ProductQueryService.findById()`로 검증한다.

### 3-3. 위시 삭제 (`WishCommandService.removeWish`)

위시 ID로 항목을 조회한다. 존재하지 않으면 `NoSuchElementException` → 404.
요청한 회원의 ID와 `wish.getMemberId()`가 다르면 `IllegalArgumentException` → 400 (또는 별도 403 예외).
본인 소유이면 삭제한다.

---

## 4. 구현 대상 파일

### gift 모듈

| 파일 | 역할 | 신규/수정 |
|------|------|----------|
| `UnauthorizedException` | 인증 실패 전용 예외 (→ 401) | **신규** |
| `ForbiddenException` | 소유권 위반 전용 예외 (→ 403) | **신규** |
| `GlobalExceptionHandler` | `UnauthorizedException` → 401, `ForbiddenException` → 403 추가 | **수정** |
| `AuthService` | `extractMember(String authorization)` 추가 | **수정** |
| `WishQueryService` | 회원별 위시 페이지 조회 | **신규** |
| `WishCommandService` | 위시 추가(멱등) + 삭제(소유권 검증) | **신규** |
| `WishController` | Service 위임, `@RequestHeader` 유지, null 체크 제거 | **수정** |
| `WishControllerTest` | MockMvc 통합 테스트 | **신규** |
| `WishQueryServiceTest` | `@Transactional` 서비스 단위 테스트 | **신규** |
| `WishCommandServiceTest` | `@Transactional` 서비스 단위 테스트 | **신규** |

### 패키지 위치

```
src/main/java/gift/
├── auth/
│   └── AuthService.java             (수정 — extractMember 추가)
├── common/
│   ├── GlobalExceptionHandler.java  (수정 — UnauthorizedException → 401, ForbiddenException → 403 추가)
│   ├── UnauthorizedException.java   (신규)
│   └── ForbiddenException.java      (신규)
└── wish/
    ├── Wish.java                    (변경 없음)
    ├── WishController.java          (수정)
    ├── WishRepository.java          (변경 없음)
    ├── WishRequest.java             (변경 없음)
    ├── WishResponse.java            (변경 없음)
    ├── WishQueryService.java        (신규)
    └── WishCommandService.java      (신규)

src/test/java/gift/wish/
    ├── WishControllerTest.java      (신규)
    ├── WishQueryServiceTest.java    (신규)
    └── WishCommandServiceTest.java  (신규)
```

### 코드 스니핏

**UnauthorizedException**
```java
// gift/common/UnauthorizedException.java
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
```

**GlobalExceptionHandler — 401, 403 핸들러 추가**
```java
@ExceptionHandler(UnauthorizedException.class)
public ResponseEntity<Void> handleUnauthorized(UnauthorizedException e) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
}

@ExceptionHandler(ForbiddenException.class)
public ResponseEntity<Void> handleForbidden(ForbiddenException e) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
}
```

**AuthService — extractMember 추가**
```java
// AuthService.java 에 추가
@Transactional(readOnly = true)
public Member extractMember(String authorization) {
    try {
        String token = authorization.replace("Bearer ", "");
        String email = jwtProvider.getEmail(token);
        return memberQueryService.findByEmail(email);
    } catch (Exception e) {
        throw new UnauthorizedException("Invalid or missing token.");
    }
}
```

**WishQueryService**
```java
@Service
@Transactional(readOnly = true)
public class WishQueryService {
    private final WishRepository wishRepository;

    public WishQueryService(WishRepository wishRepository) { ... }

    public Page<Wish> findByMemberId(Long memberId, Pageable pageable) {
        return wishRepository.findByMemberId(memberId, pageable);
    }
}
```

**WishCommandService**
```java
@Service
@Transactional
public class WishCommandService {
    private final WishRepository wishRepository;
    private final ProductQueryService productQueryService;

    public WishCommandService(WishRepository wishRepository, ProductQueryService productQueryService) { ... }

    // isNew=true면 신규 생성, false면 기존 항목
    public record WishResult(Wish wish, boolean isNew) {}

    public WishResult addWish(Long memberId, Long productId) {
        Product product = productQueryService.findById(productId);
        return wishRepository.findByMemberIdAndProductId(memberId, product.getId())
            .map(existing -> new WishResult(existing, false))
            .orElseGet(() -> new WishResult(wishRepository.save(new Wish(memberId, product)), true));
    }

    // 소유권 검증 후 삭제
    public void removeWish(Long memberId, Long wishId) {
        Wish wish = wishRepository.findById(wishId).orElseThrow();
        if (!wish.getMemberId().equals(memberId)) {
            throw new ForbiddenException("Not the owner of this wish.");
        }
        wishRepository.delete(wish);
    }
}
```

**WishController — 교체 후**
```java
@RestController
@RequestMapping("/api/wishes")
public class WishController {
    private final AuthService authService;
    private final WishQueryService wishQueryService;
    private final WishCommandService wishCommandService;

    @GetMapping
    public ResponseEntity<Page<WishResponse>> getWishes(
        @RequestHeader("Authorization") String authorization,
        Pageable pageable
    ) {
        Member member = authService.extractMember(authorization);
        Page<WishResponse> wishes = wishQueryService.findByMemberId(member.getId(), pageable)
            .map(WishResponse::from);
        return ResponseEntity.ok(wishes);
    }

    @PostMapping
    public ResponseEntity<WishResponse> addWish(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody WishRequest request
    ) {
        Member member = authService.extractMember(authorization);
        WishCommandService.WishResult result = wishCommandService.addWish(member.getId(), request.productId());
        if (!result.isNew()) {
            return ResponseEntity.ok(WishResponse.from(result.wish()));
        }
        return ResponseEntity.created(URI.create("/api/wishes/" + result.wish().getId()))
            .body(WishResponse.from(result.wish()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeWish(
        @RequestHeader("Authorization") String authorization,
        @PathVariable Long id
    ) {
        Member member = authService.extractMember(authorization);
        wishCommandService.removeWish(member.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
```

**WishControllerTest — 스니핏**
```java
class WishControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired MemberRepository memberRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired WishRepository wishRepository;

    // V2 시드 데이터 user1 (member_id=2, email=user1@example.com)
    private String tokenFor(String email) {
        return "Bearer " + jwtProvider.createToken(email);
    }

    @Test
    @DisplayName("인증된 회원의 위시리스트를 조회하면 200을 반환한다")
    void test01() throws Exception {
        // arrange
        String token = tokenFor("user1@example.com");

        // act & assert
        mockMvc.perform(get("/api/wishes")
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("토큰 없이 위시리스트를 조회하면 401을 반환한다")
    void test02() throws Exception {
        mockMvc.perform(get("/api/wishes"))
            .andExpect(status().isUnauthorized()); // 또는 400 — 고려사항 참고
    }

    @Test
    @DisplayName("유효한 상품 ID로 위시를 추가하면 201을 반환한다")
    void test03() throws Exception { ... }

    @Test
    @DisplayName("이미 추가된 상품을 다시 추가하면 200을 반환한다")
    void test04() throws Exception { ... }

    @Test
    @DisplayName("존재하지 않는 상품 ID로 위시를 추가하면 404를 반환한다")
    void test05() throws Exception { ... }

    @Test
    @DisplayName("본인 위시를 삭제하면 204를 반환한다")
    void test06() throws Exception { ... }

    @Test
    @DisplayName("다른 회원의 위시를 삭제하려 하면 403을 반환한다")
    void test07() throws Exception { ... }

    @Test
    @DisplayName("존재하지 않는 위시를 삭제하면 404를 반환한다")
    void test08() throws Exception { ... }
}
```

---

## 5. 주요 고려사항

1. **인증 실패 HTTP 상태**: ✅ **확정 — `UnauthorizedException` 신규 추가**
   - `gift/common/UnauthorizedException.java` 신규 생성
   - `GlobalExceptionHandler`에 `UnauthorizedException` → 401 핸들러 추가
   - `AuthService.extractMember()`는 토큰 파싱 실패 / 회원 미조회 시 `UnauthorizedException`을 던짐

2. **멱등 추가 시 201 vs 200 구분**: ✅ **확정 — `addWish()`가 래퍼 객체 반환**
   - `WishCommandService.addWish()`의 반환 타입을 `WishResult` 레코드로 감싸 `boolean isNew` 필드 포함
   - Controller는 `isNew`로 분기해 신규면 201, 기존이면 200 반환

3. **삭제 소유권 검증 예외**: ✅ **확정 — `ForbiddenException` 신규 추가**
   - `gift/common/ForbiddenException.java` 신규 생성
   - `GlobalExceptionHandler`에 `ForbiddenException` → 403 핸들러 추가
   - `WishCommandService.removeWish()`는 소유권 위반 시 `ForbiddenException`을 던짐
   - **원칙: 내부 구현만 변경, 외부에서 보는 HTTP 상태 코드는 기존과 동일하게 유지**

4. **`AuthenticationResolver` 잔존**: `AuthService.extractMember()`를 추가하면 `AuthenticationResolver`가 중복된다. 이번 단계에서는 그대로 두고 `WishController`만 `AuthService`를 사용하도록 변경한다.

---

## 6. 구현 순서 (TDD)

### Phase 1 — 현재 동작 고정 (구조 변경 전)

- [x] `WishControllerTest` 작성 — 현재 WishController 동작(조회/추가/삭제 happy path + 인증 실패) 테스트, **Green 확인**
- [x] 5번 고려사항의 예외 상태 코드 결정 — `UnauthorizedException` → 401, `ForbiddenException` → 403 확정

### Phase 2 — 스타일 정리 (행동 변화 없음)

- [x] WishController `// check auth`, `// check product`, `// check duplicate` 주석 제거
- [x] `var` → 명시적 타입으로 변환
- [x] `orElse(null)` + null 체크 → Optional 체이닝 패턴으로 변환
- [x] 테스트 **Green 유지** 확인

### Phase 3 — 공통 예외 추가 + AuthService 확장

- [x] `UnauthorizedException`, `ForbiddenException` 신규 생성
- [x] `GlobalExceptionHandler`에 401, 403 핸들러 추가
- [x] `AuthService.extractMember(String authorization)` 추가 — TDD (실패 시 `UnauthorizedException`)

### Phase 4 — WishQueryService TDD

- [x] `WishQueryServiceTest` — `findByMemberId` 조회 시나리오 작성 (Red)
- [x] `WishQueryService` 구현 (Green)

### Phase 5 — WishCommandService TDD

- [x] `WishCommandServiceTest` — `addWish` 신규/중복, `removeWish` 성공/소유권 위반 시나리오 작성 (Red)
- [x] `WishCommandService` 구현 (Green)

### Phase 6 — WishController 교체

- [x] `WishController` 의존성 교체: `WishRepository` + `ProductRepository` + `AuthenticationResolver` → `AuthService` + `WishQueryService` + `WishCommandService`
- [x] `WishControllerTest` **Green 유지** 확인

---

## 7. 인수 조건 (Acceptance Criteria)

- [x] `WishControllerTest` 전체 케이스 Green
- [x] `WishQueryServiceTest` 전체 케이지 Green
- [x] `WishCommandServiceTest` 전체 케이스 Green
- [x] `WishController`가 `WishRepository`, `ProductRepository`, `AuthenticationResolver`를 직접 의존하지 않음
- [x] `WishController`에 흐름 주석(`// check auth` 등)이 없음
- [x] 기존 테스트 전체 Green 유지 (`./gradlew test`)
