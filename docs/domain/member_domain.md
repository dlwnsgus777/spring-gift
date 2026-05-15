# Member 도메인 분석

> 작성일: 2026-05-10  
> 최종 수정일: 2026-05-15  
> 분석 대상 브랜치: feature/youth-6-fifth-assignment

---

## 변경 히스토리

| 날짜 | 내용 |
|------|------|
| 2026-05-10 | 최초 분석 |
| 2026-05-15 | Point @Embeddable 추출; AuthenticationResolver 제거 → AuthService.extractMember()로 통합; MemberQueryService.findByEmailOptional() 추가 |

---

## 1. 파일 구성

```
src/main/java/gift/member/
├── Member.java                — JPA Entity (@Embedded Point 보유, 포인트 충전·차감 위임)
├── MemberController.java      — REST Controller (회원가입·로그인, 인증 불필요)
├── AdminMemberController.java — Admin 전용 Controller (웹 폼 기반 CRUD + 포인트 충전)
├── MemberCommandService.java  — 상태 변경 서비스 (생성·수정·삭제·포인트 충전·Kakao 토큰 갱신)
├── MemberQueryService.java    — 읽기 전용 서비스 (@Transactional(readOnly=true))
├── MemberRepository.java      — Spring Data JPA Repository
└── MemberRequest.java         — 요청 DTO (Java Record)

src/main/java/gift/auth/
├── AuthService.java            — 회원가입·로그인·Bearer 토큰 → Member 변환 담당
├── JwtProvider.java            — JWT 발급 및 검증 (JJWT 0.13.0)
├── KakaoAuthController.java    — Kakao OAuth2 로그인 플로우 처리
├── KakaoAuthService.java       — Kakao 콜백 처리 서비스
├── KakaoLoginClient.java       — Kakao API 호출 (토큰 교환, 사용자 정보 조회)
├── KakaoLoginProperties.java   — Kakao OAuth 설정값 (clientId, redirectUri)
└── TokenResponse.java          — JWT 토큰 응답 DTO
```

테스트 파일: 없음

---

## 2. 비즈니스 로직

Member는 시스템의 인증 주체이자 포인트 보유자로, 회원가입·로그인 후 위시리스트·주문 기능을 이용할 수 있다.

### 회원가입

이메일과 비밀번호로 가입한다. 이미 등록된 이메일로 가입을 시도하면 400을 반환한다. 가입 성공 시 즉시 JWT 토큰을 발급해 반환한다—별도 로그인 단계 없이 바로 인증 상태가 된다.

이메일은 형식 검증(`@Email`)과 필수 검증(`@NotBlank`)을 모두 통과해야 한다. 비밀번호는 공백이 아닌 문자열이면 형식 제한 없이 그대로 저장된다.

### 이메일/비밀번호 로그인

등록된 이메일로 조회한 뒤 비밀번호를 비교한다. 이메일이 없거나 비밀번호가 일치하지 않으면 동일한 메시지("Invalid email or password.")로 400을 반환한다—어느 쪽이 틀렸는지 구별하지 않아 계정 존재 여부 노출을 방지한다. 로그인 성공 시 JWT 토큰을 발급한다.

Kakao OAuth로만 가입한 회원(비밀번호 null)이 이메일/비밀번호 로그인을 시도하면 비밀번호 비교 단계에서 실패한다.

### Kakao OAuth2 로그인

`GET /api/auth/kakao/login`은 클라이언트를 Kakao 인가 페이지로 리다이렉트(302)한다. Kakao가 인가 코드를 `GET /api/auth/kakao/callback`으로 전달하면 `KakaoAuthService`가 토큰 교환 → 사용자 정보 조회 → 회원 조회·자동 가입 순서로 처리한다.

해당 이메일의 회원이 이미 존재하면 기존 회원에 Kakao 액세스 토큰만 갱신한다. 존재하지 않으면 이메일만으로 새 회원을 생성한다(비밀번호 null). 두 경우 모두 Kakao 액세스 토큰을 저장하고 서비스 JWT를 발급한다.

Kakao 액세스 토큰은 주문 완료 시 Kakao 메시지 발송에 사용된다.

### 포인트 충전 (관리자)

관리자가 특정 회원에게 포인트를 충전한다. 충전 금액은 반드시 1 이상이어야 하며, 그렇지 않으면 `IllegalArgumentException`을 던진다.

### 포인트 차감 (주문 결제)

회원이 주문할 때 포인트로 결제하면 차감된다. 차감 금액이 1 미만이거나 보유 포인트를 초과하면 `IllegalArgumentException`을 던진다. 포인트는 음수가 되지 않는다. 실제 연산은 `@Embeddable Point` 값 객체에 위임된다.

### 회원 정보 수정 (관리자)

이메일과 비밀번호를 한 번에 교체한다. 부분 수정은 지원하지 않는다. 존재하지 않는 id로 요청하면 `NoSuchElementException`(→ 404)이 발생한다.

### 회원 삭제 (관리자)

회원을 삭제하면 해당 회원의 위시·주문 레코드가 FK 제약에 의해 삭제를 막는다. `ON DELETE CASCADE`가 없으므로 위시·주문이 남아 있는 회원은 DB 레벨에서 삭제할 수 없다.

---

## 3. 데이터 모델

### DB 스키마 (V1__Initialize_project_tables.sql)

```sql
create table member
(
    id                 bigint auto_increment primary key,
    email              varchar(255) not null unique,
    password           varchar(255),           -- nullable: Kakao OAuth 전용 회원은 비밀번호 없음
    kakao_access_token varchar(512),           -- nullable: 이메일/비밀번호 전용 회원은 없음
    point              int          not null default 0
);
```

### 기본 데이터 (V2__Insert_default_data.sql)

| id | email                 | point     | 용도            |
|----|-----------------------|-----------|----------------|
| 1  | admin@example.com     | 10,000,000 | 관리자 계정     |
| 2  | user1@example.com     | 5,000,000  | 일반 회원 1     |
| 3  | user2@example.com     | 3,000,000  | 일반 회원 2     |

---

## 4. 도메인 객체

```java
@Entity
public class Member {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String email;
    private String password;           // nullable
    private String kakaoAccessToken;   // nullable

    @Embedded
    private Point point = new Point(0); // gift.point.Point (@Embeddable 값 객체)
}
```

`Point`는 `gift.point` 패키지의 `@Embeddable` 불변 값 객체다. 충전·차감 연산은 새 `Point` 인스턴스를 반환하며, `Member`는 반환된 인스턴스로 필드를 교체한다.

### 공개 API

| 메서드 | 설명 |
|--------|------|
| `Member(String email, String password)` | 이메일/비밀번호 회원가입용 생성자 |
| `Member(String email)` | Kakao OAuth 자동 가입용 생성자 (비밀번호 null) |
| `update(email, password)` | 이메일·비밀번호 전체 교체 |
| `updateKakaoAccessToken(token)` | Kakao 액세스 토큰 갱신 |
| `chargePoint(amount)` | Point.charge() 위임 — amount ≤ 0이면 예외 |
| `deductPoint(amount)` | Point.deduct() 위임 — amount ≤ 0 또는 잔액 부족이면 예외 |
| `getId()` / `getEmail()` / `getPassword()` / `getKakaoAccessToken()` / `getPoint()` | Getter |

### MemberQueryService 공개 API

| 메서드 | 설명 |
|--------|------|
| `findAll()` | 전체 회원 목록 반환 |
| `findById(Long id)` | id로 회원 조회. 없으면 예외 |
| `findByEmail(String email)` | 이메일로 회원 조회. 없으면 예외 |
| `findByEmailOptional(String email)` | 이메일로 회원 조회. 없으면 `Optional.empty()` |

### MemberCommandService 공개 API

| 메서드 | 설명 |
|--------|------|
| `create(email, password)` | 이메일/비밀번호 회원 생성. 중복이면 예외 |
| `update(id, email, password)` | 회원 정보 전체 교체 |
| `delete(id)` | 회원 삭제 |
| `chargePoint(id, amount)` | 포인트 충전 |
| `createKakaoMember(email)` | Kakao OAuth 자동 가입 (password=null) |
| `updateKakaoAccessToken(id, kakaoAccessToken)` | Kakao 액세스 토큰 갱신 |

---

## 5. API 명세

### 일반 API (MemberController)

| HTTP | 경로 | 동작 | 요청 | 성공 응답 |
|------|------|------|------|----------|
| POST | `/api/members/register` | 회원가입 | `MemberRequest` | 201 + `TokenResponse` |
| POST | `/api/members/login` | 로그인 | `MemberRequest` | 200 + `TokenResponse` |

인증 없이 접근 가능하다. (`Authorization` 헤더 불필요)  
중복 이메일 또는 잘못된 자격증명 → `IllegalArgumentException` → `MemberController` 내부 `@ExceptionHandler` → 400

### Kakao OAuth API (KakaoAuthController)

| HTTP | 경로 | 동작 | 요청 | 성공 응답 |
|------|------|------|------|----------|
| GET | `/api/auth/kakao/login` | Kakao 인가 페이지 리다이렉트 | — | 302 (Location: Kakao URL) |
| GET | `/api/auth/kakao/callback` | 인가 코드 수신·JWT 발급 | `?code=` | 200 + `TokenResponse` |

### 관리자 API (AdminMemberController)

| HTTP | 경로 | 동작 | 요청 | 성공 응답 |
|------|------|------|------|----------|
| GET | `/admin/members` | 전체 목록 | — | 200 + 뷰(`member/list`) |
| GET | `/admin/members/new` | 생성 폼 | — | 200 + 뷰(`member/new`) |
| POST | `/admin/members` | 회원 생성 | Form params | redirect `/admin/members` |
| GET | `/admin/members/{id}/edit` | 수정 폼 | — | 200 + 뷰(`member/edit`) |
| POST | `/admin/members/{id}/edit` | 회원 수정 | Form params | redirect `/admin/members` |
| POST | `/admin/members/{id}/charge-point` | 포인트 충전 | `amount` (int) | redirect `/admin/members` |
| POST | `/admin/members/{id}/delete` | 회원 삭제 | — | redirect `/admin/members` |

관리자 API는 별도 인증 없이 `/admin/**` 경로로 구분된다. (Spring Security 미적용)

---

## 6. DTO 설계

### MemberRequest

```java
public record MemberRequest(
    @NotBlank @Email String email,   // 이메일 형식 필수
    @NotBlank String password        // 비어있지 않은 문자열
) {
}
```

- 회원가입·로그인 양쪽에서 동일 DTO를 공유한다.
- `@Valid`로 컨트롤러 진입 전 검증한다.

### TokenResponse (auth 패키지)

```java
public record TokenResponse(String token) {
}
```

- Member 도메인은 별도 Response DTO가 없다. 인증 결과는 항상 JWT 토큰으로만 반환된다.

---

## 7. 다른 도메인과의 관계

```
Member (1)
    ├── Wish (N)    — wish.member_id FK NOT NULL
    └── Order (N)   — orders.member_id FK NOT NULL

Member (@Embedded Point)
    ← 충전: AdminMemberController → MemberCommandService
    ← 차감: OrderCommandService (주문 생성 트랜잭션 내)
```

- `Wish`와 `Order`는 모두 `member_id`를 NOT NULL FK로 갖는다—회원 없이 위시·주문 레코드가 존재할 수 없다.
- `Order`는 `member_id`를 `Long` 타입으로 보유한다 (`Order.memberId`). Entity 참조 대신 식별자만 저장해 N+1 문제를 예방하는 의도적 설계다.
- 주문 완료 후 `NotifySendService`는 `member.getKakaoAccessToken()`으로 Kakao 알림을 발송한다.
- `AuthService.extractMember()`는 `Authorization: Bearer <JWT>` 헤더에서 이메일을 추출한 뒤 `MemberQueryService.findByEmail()`로 Member를 조회해 반환한다. 인증이 필요한 컨트롤러(WishController, OrderController)에서 공통으로 사용된다.
