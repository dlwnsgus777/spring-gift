# spec: Member 도메인 정리 (3단계)

## 1. 기능 개요

Member 도메인에 통합 테스트를 추가해 현재 API 동작을 Green 테스트로 고정한 뒤, Controller에 분산된 비즈니스 로직을 서비스 계층으로 추출한다.
- **AuthService** (auth 패키지) — 회원가입·로그인·JWT 발급 담당 (회원 저장 포함)
- **MemberCommandService / MemberQueryService** (member 패키지) — Admin용 CRUD·조회·포인트 충전 담당

스타일 정리에서는 의미 없는 클래스 레벨 Javadoc을 제거하고, 로컬 `@ExceptionHandler`를 `GlobalExceptionHandler`로 이관해 예외 처리를 일원화한다.

### 서비스 책임 분리 설계

```
MemberController → AuthService → MemberRepository  (register: 저장 직접)
                              → MemberQueryService → MemberRepository  (login: 조회)
                              → JwtProvider

AdminMemberController → MemberQueryService
                      → MemberCommandService → MemberRepository
```

### 기능 구성

| 파일 | 작업 내용 |
|------|----------|
| `AuthService` | 신규 — register(중복 체크+저장+JWT 발급), login(검증+JWT 발급) |
| `MemberQueryService` | 신규 — findAll, findById, findByEmail |
| `MemberCommandService` | 신규 — create(Admin용), update, delete, chargePoint |
| `MemberController` | 수정 — AuthService 위임 |
| `AdminMemberController` | 수정 — Query/Command 서비스 위임, Javadoc 제거 |
| `GlobalExceptionHandler` | 수정 — `IllegalArgumentException` → 400 추가 |

---

## 2. API 설계

### API 1 - 회원가입

```
POST /api/members/register
Content-Type: application/json
```

**Request**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response 201 Created**
```json
{
  "token": "eyJhbGci..."
}
```

**엣지 케이스**
| 시나리오 | 응답 |
|----------|------|
| 이미 등록된 이메일 | 400 + `"Email is already registered."` |
| 이메일 형식 오류 (`@Email` 실패) | 400 |
| 비밀번호 빈 값 (`@NotBlank` 실패) | 400 |

---

### API 2 - 로그인

```
POST /api/members/login
Content-Type: application/json
```

**Request**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response 200 OK**
```json
{
  "token": "eyJhbGci..."
}
```

**엣지 케이스**
| 시나리오 | 응답 |
|----------|------|
| 등록되지 않은 이메일 | 400 + `"Invalid email or password."` |
| 비밀번호 불일치 | 400 + `"Invalid email or password."` |
| Kakao 전용 회원 (비밀번호 null) 로 시도 | 400 + `"Invalid email or password."` |

---

### API 3 - Admin CRUD (뷰 기반)

```
GET    /admin/members               → 200 (member/list 뷰)
GET    /admin/members/new           → 200 (member/new 뷰)
POST   /admin/members               → 302 redirect /admin/members (성공)
                                      200 member/new 뷰 (중복 이메일)
GET    /admin/members/{id}/edit     → 200 (member/edit 뷰)
POST   /admin/members/{id}/edit     → 302 redirect /admin/members
POST   /admin/members/{id}/charge-point → 302 redirect /admin/members
POST   /admin/members/{id}/delete   → 302 redirect /admin/members
```

---

## 3. 비즈니스 로직

### 3-1. 회원가입 — AuthService.register()

1. `memberRepository.existsByEmail(email)` → 중복이면 `IllegalArgumentException`
2. `new Member(email, password)` 저장 (`memberRepository.save()`)
3. `jwtProvider.createToken(member.getEmail())` 호출
4. `TokenResponse` 반환

### 3-2. 로그인 — AuthService.login()

1. `memberQueryService.findByEmail(email)` → 없으면 `IllegalArgumentException("Invalid email or password.")`
2. `member.getPassword() == null || !member.getPassword().equals(password)` → 실패 시 동일 메시지 예외
   - 계정 존재 여부 노출 방지를 위해 이메일 없음과 비밀번호 불일치를 동일 메시지로 반환
3. `jwtProvider.createToken(member.getEmail())` → `TokenResponse` 반환

### 3-3. Admin 회원 생성 — MemberCommandService.create()

1. `memberRepository.existsByEmail(email)` → 중복이면 `IllegalArgumentException`
2. `new Member(email, password)` 저장 후 Member 반환

### 3-4. 포인트 충전 — PointCommandService.chargePoint()

1. `memberRepository.findById(id)` → 없으면 `NoSuchElementException` (`orElseThrow()`)
2. `member.chargePoint(amount)` — amount ≤ 0이면 내부에서 `IllegalArgumentException`
3. 저장

### 3-5. 예외 처리 일원화

| 예외 | HTTP | 처리 위치 |
|------|------|----------|
| `NoSuchElementException` | 404 | `GlobalExceptionHandler` (기존) |
| `IllegalArgumentException` | 400 | `GlobalExceptionHandler` (신규 추가) |

`MemberController`의 로컬 `@ExceptionHandler(IllegalArgumentException.class)`는 제거한다.

`AdminMemberController.create()`는 중복 이메일 시 폼 에러 모델을 표시해야 하므로 `MemberCommandService.create()` 호출을 `try-catch`로 감싸 뷰 에러를 직접 처리한다.

---

## 4. 구현 대상 파일

### auth 패키지

| 파일 | 역할 | 신규/수정 |
|------|------|----------|
| `AuthService` | register/login + JWT 발급 | 신규 |

### member 패키지

| 파일 | 역할 | 신규/수정 |
|------|------|----------|
| `MemberQueryService` | findAll, findById, findByEmail | 신규 |
| `MemberCommandService` | create(중복 체크+저장), update, delete, chargePoint | 신규 |
| `MemberController` | Javadoc 제거, 로컬 핸들러 제거, AuthService 위임 | 수정 |
| `AdminMemberController` | Javadoc 제거, 서비스 위임 | 수정 |

### common 패키지

| 파일 | 역할 | 신규/수정 |
|------|------|----------|
| `GlobalExceptionHandler` | `IllegalArgumentException` → 400 처리 추가 | 수정 |

### 테스트

| 파일 | 역할 | 신규/수정 |
|------|------|----------|
| `MemberControllerTest` | register / login API 통합 테스트 | 신규 |
| `AdminMemberControllerTest` | 관리자 CRUD 통합 테스트 | 신규 |

### 패키지 위치

```
src/main/java/gift/
├── auth/
│   ├── AuthService.java                  (신규)
│   ├── AuthenticationResolver.java
│   ├── JwtProvider.java
│   ├── KakaoAuthController.java
│   ├── KakaoLoginClient.java
│   ├── KakaoLoginProperties.java
│   └── TokenResponse.java
├── common/
│   └── GlobalExceptionHandler.java       (수정)
└── member/
    ├── Member.java
    ├── MemberController.java             (수정)
    ├── AdminMemberController.java        (수정)
    ├── MemberRepository.java
    ├── MemberRequest.java
    ├── MemberQueryService.java           (신규)
    └── MemberCommandService.java         (신규)

src/test/java/gift/
└── member/
    ├── MemberControllerTest.java         (신규)
    └── AdminMemberControllerTest.java    (신규)
```

### 코드 스니핏

**GlobalExceptionHandler.java (수정)**
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Void> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
```

**MemberQueryService.java (신규)**
```java
@Service
@Transactional(readOnly = true)
public class MemberQueryService {

    private final MemberRepository memberRepository;

    public MemberQueryService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public List<Member> findAll() {
        return memberRepository.findAll();
    }

    public Member findById(Long id) {
        return memberRepository.findById(id).orElseThrow();
    }

    public Member findByEmail(String email) {
        return memberRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));
    }
}
```

**MemberCommandService.java (신규) — Admin용 CRUD**
```java
@Service
@Transactional
public class MemberCommandService {

    private final MemberRepository memberRepository;

    public MemberCommandService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    // Admin 회원 생성 — AuthService.register()와 별개로 관리자 직접 생성
    public Member create(String email, String password) {
        if (memberRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered.");
        }
        return memberRepository.save(new Member(email, password));
    }

    public Member update(Long id, String email, String password) {
        Member member = memberRepository.findById(id).orElseThrow();
        member.update(email, password);
        return memberRepository.save(member);
    }

    public void chargePoint(Long id, int amount) {
        Member member = memberRepository.findById(id).orElseThrow();
        member.chargePoint(amount);
        memberRepository.save(member);
    }

    public void delete(Long id) {
        memberRepository.deleteById(id);
    }
}
```

**AuthService.java (신규)**
```java
@Service
@Transactional
public class AuthService {

    private final MemberRepository memberRepository;
    private final MemberQueryService memberQueryService;
    private final JwtProvider jwtProvider;

    public AuthService(MemberRepository memberRepository,
                       MemberQueryService memberQueryService,
                       JwtProvider jwtProvider) {
        this.memberRepository = memberRepository;
        this.memberQueryService = memberQueryService;
        this.jwtProvider = jwtProvider;
    }

    public TokenResponse register(MemberRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email is already registered.");
        }
        Member member = memberRepository.save(new Member(request.email(), request.password()));
        return new TokenResponse(jwtProvider.createToken(member.getEmail()));
    }

    public TokenResponse login(MemberRequest request) {
        Member member = memberQueryService.findByEmail(request.email());
        if (member.getPassword() == null || !member.getPassword().equals(request.password())) {
            throw new IllegalArgumentException("Invalid email or password.");
        }
        return new TokenResponse(jwtProvider.createToken(member.getEmail()));
    }
}
```

**MemberController.java (수정 후)**
```java
@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final AuthService authService;

    public MemberController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody MemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody MemberRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
```

**AdminMemberController.java (수정 후 골격)**
```java
@Controller
@RequestMapping("/admin/members")
public class AdminMemberController {

    private final MemberQueryService memberQueryService;
    private final MemberCommandService memberCommandService;

    public AdminMemberController(MemberQueryService memberQueryService,
                                 MemberCommandService memberCommandService) { ... }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("members", memberQueryService.findAll());
        return "member/list";
    }

    @PostMapping
    public String create(@RequestParam String email, @RequestParam String password, Model model) {
        try {
            memberCommandService.create(email, password);
        } catch (IllegalArgumentException e) {
            // 폼 에러 표시 — GlobalExceptionHandler가 아닌 뷰 컨트롤러 직접 처리
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", email);
            return "member/new";
        }
        return "redirect:/admin/members";
    }

    @PostMapping("/{id}/charge-point")
    public String chargePoint(@PathVariable Long id, @RequestParam int amount) {
        memberCommandService.chargePoint(id, amount);
        return "redirect:/admin/members";
    }

    // update, delete — memberCommandService 위임
}
```

**MemberControllerTest.java (신규)**
```java
class MemberControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("유효한 이메일과 비밀번호로 회원가입하면 201과 JWT 토큰을 반환한다")
    void test01() throws Exception {
        // arrange
        MemberRequest request = new MemberRequest("test_" + UUID.randomUUID() + "@ex.com", "pass");

        // act & assert
        mockMvc.perform(post("/api/members/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("이미 등록된 이메일로 회원가입하면 400을 반환한다")
    void test02() throws Exception { /* ... */ }

    @Test
    @DisplayName("이메일 형식이 아닌 값으로 회원가입하면 400을 반환한다")
    void test03() throws Exception { /* ... */ }

    @Test
    @DisplayName("등록된 이메일과 비밀번호로 로그인하면 200과 JWT 토큰을 반환한다")
    void test04() throws Exception { /* ... */ }

    @Test
    @DisplayName("등록되지 않은 이메일로 로그인하면 400을 반환한다")
    void test05() throws Exception { /* ... */ }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 400을 반환한다")
    void test06() throws Exception { /* ... */ }
}
```

**AdminMemberControllerTest.java (신규)**
```java
class AdminMemberControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("전체 회원 목록 페이지를 조회하면 200을 반환한다")
    void test01() throws Exception { /* ... */ }

    @Test
    @DisplayName("회원 생성 폼 페이지를 요청하면 200을 반환한다")
    void test02() throws Exception { /* ... */ }

    @Test
    @DisplayName("유효한 이메일로 회원을 생성하면 목록 페이지로 리다이렉트한다")
    void test03() throws Exception { /* ... */ }

    @Test
    @DisplayName("이미 등록된 이메일로 회원을 생성하면 200을 반환한다")
    void test04() throws Exception { /* ... */ }

    @Test
    @DisplayName("존재하는 회원의 정보를 수정하면 목록 페이지로 리다이렉트한다")
    void test05() throws Exception { /* ... */ }

    @Test
    @DisplayName("회원에게 포인트를 충전하면 목록 페이지로 리다이렉트한다")
    void test06() throws Exception { /* ... */ }

    @Test
    @DisplayName("회원을 삭제하면 목록 페이지로 리다이렉트한다")
    void test07() throws Exception { /* ... */ }
}
```

---

## 5. 주요 고려사항

1. **패키지 간 의존 방향**: `AuthService`(auth) → `MemberCommandService`/`MemberQueryService`(member), `PointCommandService`(point) → `MemberRepository`(member). Spring Bean 순환 없는 단방향 의존이다.

2. **AdminMemberController 중복 이메일 처리**: `MemberCommandService.create()`가 `IllegalArgumentException`을 던지면 `GlobalExceptionHandler`가 400을 반환해 뷰 응답이 깨진다. 폼 에러 표시가 필요한 `AdminMemberController.create()`는 `try-catch`로 직접 처리한다.

3. **AdminMemberController 삭제 테스트**: 시드 데이터 회원(user1, user2)은 위시·주문 FK가 있어 삭제 시 DB 에러 발생. 테스트에서는 FK 없는 신규 회원을 `memberRepository.save()`로 생성해 삭제 대상으로 사용한다.

4. **`findByEmail` 예외 메시지**: `MemberQueryService.findByEmail()`이 던지는 메시지는 로그인 실패 메시지(`"Invalid email or password."`)다. AuthService에서 호출 시 메시지 일관성이 유지된다.

---

## 6. 구현 순서 (TDD)

### 스타일 정리 (구조 변경 없음)

- [x] `MemberController`, `AdminMemberController`, `Member`, `MemberRepository`, `MemberRequest` 클래스 레벨 Javadoc 제거
- [x] `GlobalExceptionHandler`에 `IllegalArgumentException` → 400 처리 추가
- [x] `MemberController` 로컬 `@ExceptionHandler` 제거

### 통합 테스트 작성 (현재 동작 고정)

- [x] `MemberControllerTest` 작성 — register 성공/실패(3케이스), login 성공/실패(2케이스) 총 6개
- [x] `AdminMemberControllerTest` 작성 — HTTP 상태 + redirect 7개 케이스
- [x] 테스트 Green 확인

### 서비스 추출 (구조 변경, 동작 불변)

- [x] `MemberQueryService` 작성 — `findAll()`, `findById()`, `findByEmail()`
- [x] `MemberCommandService` 작성 — `create()`, `update()`, `delete()`, `chargePoint()`
- [x] `AuthService` 작성 — `register()`(저장+JWT), `login()`(검증+JWT) + MemberRepository, MemberQueryService, JwtProvider 의존
- [x] `MemberController` 리팩터링 — `AuthService` 주입, Repository/JwtProvider 직접 의존 제거
- [x] `AdminMemberController` 리팩터링 — `MemberQueryService` + `MemberCommandService` 주입
- [x] 통합 테스트 Green 재확인

---

## 7. 인수 조건 (Acceptance Criteria)

- [x] `MemberControllerTest` 전체 Green
- [x] `AdminMemberControllerTest` 전체 Green
- [x] `MemberController`가 `AuthService`만 의존 (Repository, JwtProvider 직접 주입 없음)
- [x] `AdminMemberController`가 Repository를 직접 주입하지 않음
- [x] `MemberController`에 `@ExceptionHandler`가 없음
- [x] `GlobalExceptionHandler`가 `IllegalArgumentException` → 400을 처리함
- [x] JWT 발급이 `AuthService`에서 이루어짐 (Controller 레벨에서 JwtProvider 미호출)
- [x] 포인트 충전이 `MemberCommandService.chargePoint()`에서 이루어짐 (4단계에서 point 패키지로 분리 예정)
- [x] 모든 클래스 레벨 Javadoc (`@author`, `@since`) 제거됨
