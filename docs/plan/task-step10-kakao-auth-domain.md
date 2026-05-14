# spec: KakaoAuth 도메인 10단계 — 통합 테스트 + KakaoAuthService 추출

## 1. 기능 개요

`KakaoAuthController.callback()`이 `MemberRepository`와 `JwtProvider`를 직접 의존하고 비즈니스 로직을 인라인으로 처리한다. 이번 단계에서는 callback 흐름(토큰 교환 → 회원 조회/생성 → JWT 발급)을 `KakaoAuthService`로 이동해 컨트롤러의 직접 의존을 제거한다. 테스트용 `FakeKakaoLoginClient`는 `KakaoLoginClient`를 상속해 HTTP 호출 메서드만 오버라이드한다.

### 작업 구성

| 작업 | 설명 |
|------|------|
| **통합 테스트 작성** | `KakaoAuthControllerTest` — login 302, callback 흐름 |
| **FakeKakaoLoginClient 생성** | `KakaoLoginClient` 상속, HTTP 메서드 오버라이드 |
| **KakaoAuthService 추출** | callback 로직(토큰 교환 → 회원 조회/생성 → JWT 발급) 서비스로 이동 |
| **MemberCommandService / MemberQueryService 확장** | Kakao 회원 생성·토큰 갱신 메서드 추가 |
| **Controller 정리** | `MemberRepository`, `JwtProvider` 직접 의존 제거 |

---

## 2. API 설계

### API 1 — Kakao 로그인 시작

```
GET /api/auth/kakao/login
```

- Kakao 인증 URL로 즉시 302 리다이렉트한다
- scope: `account_email,talk_message`

**Response 302 Found**
```
Location: https://kauth.kakao.com/oauth/authorize?response_type=code&client_id=...&redirect_uri=...&scope=account_email,talk_message
```

**엣지 케이스**

| 조건 | 응답 |
|------|------|
| 인증 헤더 없음 | 302 (인증 불필요 엔드포인트) |

---

### API 2 — Kakao OAuth2 콜백

```
GET /api/auth/kakao/callback?code={authorization_code}
```

- Kakao 인가 코드로 access token을 교환하고 서비스 JWT를 반환한다
- 최초 로그인이면 이메일만으로 회원을 자동 생성한다(password=null)
- 기존 회원이면 kakao access token만 갱신한다

**Response 200 OK**
```json
{
  "token": "eyJhbGci..."
}
```

**엣지 케이스**

| 조건 | 응답 |
|------|------|
| code 파라미터 없음 | 400 |
| 신규 이메일 (자동 가입) | 200 + token |
| 기존 이메일 (로그인) | 200 + token |

---

## 3. 비즈니스 로직

### 3-1. callback 처리 흐름 (`KakaoAuthService.callback()`)

1. `kakaoLoginClient.requestAccessToken(code)` — 인가 코드 → Kakao access token 교환
2. `kakaoLoginClient.requestUserInfo(accessToken)` — Kakao access token → 이메일 조회
3. `memberQueryService.findByEmailOptional(email)` — 이메일로 회원 조회 (Optional 반환)
   - 존재하지 않으면 `memberCommandService.createKakaoMember(email)` — password=null 신규 생성
4. `memberCommandService.updateKakaoAccessToken(member.getId(), accessToken)` — 최신 토큰 갱신
5. `jwtProvider.createToken(member.getEmail())` — 서비스 JWT 발급
6. `new TokenResponse(token)` 반환

### 3-2. FakeKakaoLoginClient — 상속 기반 테스트 더블

`KakaoLoginClient`는 Kakao 전용 클라이언트로 인터페이스 추상화 실익이 없다. 테스트에서는 `KakaoLoginClient`를 상속해 `requestAccessToken`과 `requestUserInfo`만 오버라이드한 `FakeKakaoLoginClient`를 `@TestConfiguration`으로 교체 주입한다.

```
KakaoLoginClient (class, 변경 없음)
└── FakeKakaoLoginClient (extends, test-only)   — HTTP 메서드만 오버라이드
```

### 3-3. MemberCommandService / MemberQueryService 확장

| 메서드 | 위치 | 설명 |
|--------|------|------|
| `findByEmailOptional(String email)` | `MemberQueryService` | `Optional<Member>` 반환 |
| `createKakaoMember(String email)` | `MemberCommandService` | password=null 회원 생성 |
| `updateKakaoAccessToken(Long id, String token)` | `MemberCommandService` | Dirty Checking으로 토큰 갱신 |

---

## 4. 구현 대상 파일

### auth 모듈

| 파일 | 역할 | 변경 |
|------|------|------|
| `KakaoLoginClient.java` | Kakao HTTP 클라이언트 | 변경 없음 |
| `KakaoAuthService.java` | callback 비즈니스 로직 | 신규 |
| `KakaoAuthController.java` | HTTP 계층 — KakaoAuthService 위임 | 수정 |

### member 모듈

| 파일 | 역할 | 변경 |
|------|------|------|
| `MemberQueryService.java` | `findByEmailOptional()` 추가 | 수정 |
| `MemberCommandService.java` | `createKakaoMember()`, `updateKakaoAccessToken()` 추가 | 수정 |

### 테스트

| 파일 | 역할 | 변경 |
|------|------|------|
| `FakeKakaoLoginClient.java` | `KakaoLoginClient` 상속, HTTP 메서드 오버라이드 | 신규 |
| `KakaoAuthControllerTest.java` | API 통합 테스트 (MockMvc) | 신규 |
| `KakaoAuthServiceTest.java` | 서비스 단위 테스트 | 신규 |

### 패키지 위치

```
src/main/java/gift/auth/
├── AuthService.java                 (변경 없음)
├── JwtProvider.java                 (변경 없음)
├── KakaoAuthController.java         (수정)
├── KakaoAuthService.java            (신규)
├── KakaoLoginClient.java            (변경 없음)
├── KakaoLoginProperties.java        (변경 없음)
└── TokenResponse.java               (변경 없음)

src/main/java/gift/member/
├── MemberCommandService.java        (수정 — 메서드 추가)
└── MemberQueryService.java          (수정 — 메서드 추가)

src/test/java/gift/auth/
├── FakeKakaoLoginClient.java        (신규)
├── KakaoAuthControllerTest.java     (신규)
└── KakaoAuthServiceTest.java        (신규)
```

### 코드 스니핏

**FakeKakaoLoginClient.java** (신규 — test 패키지)

```java
package gift.auth;

// KakaoLoginClient 상속 — RestClient HTTP 호출 메서드만 오버라이드
// 생성자는 null을 넘겨 부모의 HTTP 초기화를 건너뜀
class FakeKakaoLoginClient extends KakaoLoginClient {

    private final String fixedEmail;

    FakeKakaoLoginClient(String fixedEmail) {
        super(null, null);  // properties, builder — HTTP 호출 안 하므로 null
        this.fixedEmail = fixedEmail;
    }

    @Override
    public KakaoTokenResponse requestAccessToken(String code) {
        return new KakaoTokenResponse("fake-kakao-access-token");
    }

    @Override
    public KakaoUserResponse requestUserInfo(String accessToken) {
        return new KakaoUserResponse(new KakaoUserResponse.KakaoAccount(fixedEmail));
    }
}
```

**KakaoAuthService.java** (신규)

```java
@Service
@Transactional
public class KakaoAuthService {
    private final KakaoLoginClient kakaoLoginClient;
    private final MemberQueryService memberQueryService;
    private final MemberCommandService memberCommandService;
    private final JwtProvider jwtProvider;

    public KakaoAuthService(
        KakaoLoginClient kakaoLoginClient,
        MemberQueryService memberQueryService,
        MemberCommandService memberCommandService,
        JwtProvider jwtProvider
    ) { ... }

    public TokenResponse callback(String code) {
        KakaoLoginClient.KakaoTokenResponse kakaoToken = kakaoLoginClient.requestAccessToken(code);
        String email = kakaoLoginClient.requestUserInfo(kakaoToken.accessToken()).email();

        Member member = memberQueryService.findByEmailOptional(email)
            .orElseGet(() -> memberCommandService.createKakaoMember(email));

        memberCommandService.updateKakaoAccessToken(member.getId(), kakaoToken.accessToken());

        return new TokenResponse(jwtProvider.createToken(member.getEmail()));
    }
}
```

**KakaoAuthController.java** (수정 후)

```java
@RestController
@RequestMapping(path = "/api/auth/kakao")
public class KakaoAuthController {
    private final KakaoLoginProperties properties;
    private final KakaoAuthService kakaoAuthService;

    public KakaoAuthController(KakaoLoginProperties properties, KakaoAuthService kakaoAuthService) { ... }

    @GetMapping(path = "/login")
    public ResponseEntity<Void> login() {
        String kakaoAuthUrl = UriComponentsBuilder.fromUriString("https://kauth.kakao.com/oauth/authorize")
            .queryParam("response_type", "code")
            .queryParam("client_id", properties.clientId())
            .queryParam("redirect_uri", properties.redirectUri())
            .queryParam("scope", "account_email,talk_message")
            .build()
            .toUriString();
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, kakaoAuthUrl)
            .build();
    }

    @GetMapping(path = "/callback")
    public ResponseEntity<TokenResponse> callback(@RequestParam("code") String code) {
        return ResponseEntity.ok(kakaoAuthService.callback(code));
    }
}
```

**MemberQueryService.java** (추가 메서드)

```java
public Optional<Member> findByEmailOptional(String email) {
    return memberRepository.findByEmail(email);
}
```

**MemberCommandService.java** (추가 메서드)

```java
// Kakao OAuth2로 최초 로그인한 회원 생성 — password=null
public Member createKakaoMember(String email) {
    return memberRepository.save(new Member(email));
}

// Dirty Checking으로 최신 Kakao access token 갱신
public Member updateKakaoAccessToken(Long id, String kakaoAccessToken) {
    Member member = memberRepository.findById(id).orElseThrow();
    member.updateKakaoAccessToken(kakaoAccessToken);
    return member;
}
```

**KakaoAuthControllerTest.java** (신규)

```java
@Import(KakaoAuthControllerTest.FakeKakaoConfig.class)
class KakaoAuthControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private MemberRepository memberRepository;

    static final String FAKE_EMAIL = "kakao_" + UUID.randomUUID() + "@test.com";

    @TestConfiguration
    static class FakeKakaoConfig {
        @Bean
        @Primary
        KakaoLoginClient kakaoLoginClient() {
            return new FakeKakaoLoginClient(FAKE_EMAIL);
        }
    }

    @Test
    @DisplayName("GET /api/auth/kakao/login은 Kakao 인증 URL로 302 리다이렉트한다")
    void test01() throws Exception { ... }

    @Test
    @DisplayName("신규 이메일 code로 callback하면 회원이 자동 생성되고 200과 JWT 토큰을 반환한다")
    void test02() throws Exception { ... }

    @Test
    @DisplayName("기존 이메일 code로 callback하면 기존 회원의 kakao token이 갱신되고 200을 반환한다")
    void test03() throws Exception { ... }

    @Test
    @DisplayName("code 파라미터 없이 callback을 호출하면 400을 반환한다")
    void test04() throws Exception { ... }
}
```

**KakaoAuthServiceTest.java** (신규)

```java
@Import(KakaoAuthServiceTest.FakeKakaoConfig.class)
@Transactional
class KakaoAuthServiceTest extends AbstractIntegrationTest {

    @Autowired private KakaoAuthService kakaoAuthService;
    @Autowired private MemberRepository memberRepository;

    @TestConfiguration
    static class FakeKakaoConfig {
        @Bean
        @Primary
        KakaoLoginClient kakaoLoginClient() {
            return new FakeKakaoLoginClient("kakao_service_" + UUID.randomUUID() + "@test.com");
        }
    }

    @Test
    @DisplayName("신규 이메일이면 회원을 자동 생성하고 JWT 토큰을 반환한다")
    void test01() { ... }

    @Test
    @DisplayName("기존 이메일이면 회원을 새로 만들지 않고 JWT 토큰을 반환한다")
    void test02() { ... }

    @Test
    @DisplayName("callback 후 kakaoAccessToken이 회원에 저장된다")
    void test03() { ... }
}
```

---

## 5. 주요 고려사항

1. **`FakeKakaoLoginClient` 생성자에 null 전달**: `KakaoLoginClient(null, null)`로 부모 생성자를 호출하면 `RestClient.Builder.build()` 시점에 NPE가 발생할 수 있다. `KakaoLoginClient` 생성자가 `builder.build()`를 즉시 호출한다면 Fake 생성 시점에 터진다. 이 경우 생성자를 `protected`로 노출하는 별도 no-arg 생성자를 추가하거나, `RestClient.Builder` mock을 넘기는 방식으로 우회한다.

2. **`updateKakaoAccessToken` Dirty Checking**: `KakaoAuthService`가 `@Transactional`이므로 `memberCommandService.updateKakaoAccessToken()` 안에서 `member.updateKakaoAccessToken(token)`을 호출하면 트랜잭션 종료 시 자동 반영된다. `memberRepository.save(member)`를 명시적으로 호출할 필요가 없다.

3. **`KakaoAuthControllerTest` 격리**: `FakeKakaoLoginClient`가 고정 이메일을 반환하므로 test02(자동 생성)와 test03(기존 회원)이 같은 이메일로 충돌할 수 있다. test03 실행 전에 해당 이메일로 회원을 미리 `memberRepository.save()`해 두거나, `@BeforeEach`에서 DB를 정리한다.

4. **`login()` URL 빌드 책임**: URL 파라미터 조립은 순수 HTTP 응답 구성이므로 서비스로 이동하지 않는다. `KakaoLoginProperties`는 컨트롤러가 직접 보유한다.

---

## 6. 구현 순서 (TDD)

> **원칙**: 구조적 변경(동작 변화 없음)을 먼저, 새 동작을 나중에.

### 1단계 — FakeKakaoLoginClient 생성 및 통합 테스트 작성 (기존 동작 Green 고정)

- [ ] `FakeKakaoLoginClient extends KakaoLoginClient` 생성 (test 패키지)
- [ ] `KakaoAuthControllerTest` 작성 — test01 (302 리다이렉트)
- [ ] `KakaoAuthControllerTest.test02` 작성 — callback 신규 회원 자동 생성 200
- [ ] `KakaoAuthControllerTest.test03` 작성 — callback 기존 회원 로그인 200
- [ ] `KakaoAuthControllerTest.test04` 작성 — code 파라미터 없음 400
- [ ] 전체 테스트 Green 확인

### 2단계 — MemberQueryService / MemberCommandService 확장 (구조 변경)

- [ ] `MemberQueryService.findByEmailOptional()` 추가
- [ ] `MemberCommandService.createKakaoMember()` 추가
- [ ] `MemberCommandService.updateKakaoAccessToken()` 추가
- [ ] 기존 서비스 테스트 Green 확인

### 3단계 — KakaoAuthService 추출 (구조 변경)

- [ ] `KakaoAuthServiceTest` 작성 — test01, test02, test03 → **Red** 확인 (클래스 미존재)
- [ ] `KakaoAuthService` 생성 — `callback()` 구현
- [ ] `KakaoAuthServiceTest` **Green** 확인
- [ ] `KakaoAuthController.callback()` → `kakaoAuthService.callback(code)` 위임
- [ ] `KakaoAuthController`에서 `MemberRepository`, `JwtProvider` 의존 제거
- [ ] `KakaoAuthControllerTest` 전체 **Green** 확인

---

## 7. 인수 조건 (Acceptance Criteria)

- [ ] `KakaoAuthController`에 `MemberRepository` 직접 의존이 없다
- [ ] `KakaoAuthController`에 `JwtProvider` 직접 의존이 없다
- [ ] `KakaoAuthController.callback()`이 `kakaoAuthService.callback(code)` 한 줄로 위임한다
- [ ] `KakaoLoginClient`는 클래스 그대로 유지된다 — 인터페이스 전환 없음
- [ ] `FakeKakaoLoginClient`가 테스트 패키지에만 존재한다
- [ ] `KakaoAuthService`가 `MemberRepository`를 직접 참조하지 않는다 — `MemberQueryService`, `MemberCommandService` 경유
- [ ] `/api/auth/kakao/login` → 302 리다이렉트 테스트가 Green이다
- [ ] `/api/auth/kakao/callback` 신규 이메일 자동 생성 테스트가 Green이다
- [ ] `/api/auth/kakao/callback` 기존 이메일 로그인 + kakao token 갱신 테스트가 Green이다
- [ ] 전체 테스트 Green
