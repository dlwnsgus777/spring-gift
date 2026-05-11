# KakaoAuth (인증) 도메인 분석

> 작성일: 2026-05-11  
> 분석 대상 브랜치: feature/youth-6-fifth-assignment

---

## 1. 파일 구성

```
src/main/java/gift/auth/
├── AuthService.java              — 일반 이메일/비밀번호 회원가입·로그인 서비스
├── AuthenticationResolver.java  — Bearer 토큰 → Member 변환 (컨트롤러 파라미터 주입)
├── JwtProvider.java              — JWT 발급·검증 (HMAC-SHA)
├── KakaoAuthController.java     — Kakao OAuth2 흐름 처리 컨트롤러
├── KakaoLoginClient.java        — Kakao API HTTP 클라이언트 (토큰 교환·사용자 정보 조회)
├── KakaoLoginProperties.java    — Kakao OAuth2 설정값 (clientId, clientSecret, redirectUri)
└── TokenResponse.java           — JWT 토큰 응답 DTO
```

테스트 파일: `src/test/java/gift/auth/AuthServiceTest.java`

---

## 2. 비즈니스 로직

auth 도메인은 이메일/비밀번호 기반 일반 인증과 Kakao OAuth2 소셜 로그인을 모두 지원하며, 두 경로 모두 최종적으로 서비스 JWT를 발급해 반환한다.

### 일반 회원가입

회원가입은 이메일과 비밀번호를 받아 새 Member를 생성하고 JWT를 발급한다. 이메일은 시스템 전체에서 유일해야 하며, 이미 등록된 이메일로 가입을 시도하면 요청이 거부된다.

### 일반 로그인

이메일로 Member를 조회한 뒤 비밀번호를 비교해 JWT를 발급한다. 이메일 미존재와 비밀번호 불일치 모두 동일한 메시지("Invalid email or password.")로 응답해 이메일 열거 공격(email enumeration)을 방지한다. Kakao OAuth2로 가입한 회원은 `password` 컬럼이 null이므로 일반 로그인이 불가능하다.

### Kakao OAuth2 로그인 시작

`/api/auth/kakao/login` 요청이 들어오면 즉시 Kakao 인증 URL(`https://kauth.kakao.com/oauth/authorize`)로 302 리다이렉트한다. 이때 `account_email`(이메일 조회)과 `talk_message`(카카오 메시지 발송) 두 가지 권한 범위(scope)를 함께 요청한다. `talk_message` 권한은 주문 완료 후 카카오 메시지를 보내기 위해 미리 획득한다.

### Kakao OAuth2 콜백 처리

Kakao 인증 서버가 발급한 인가 코드(code)를 받아 다음 순서로 처리한다:

1. 인가 코드로 Kakao access token을 교환한다 (`https://kauth.kakao.com/oauth/token`).
2. access token으로 카카오 계정 이메일을 조회한다 (`https://kapi.kakao.com/v2/user/me`).
3. 해당 이메일로 등록된 Member를 조회한다. 존재하지 않으면 이메일만으로 새 Member를 자동 생성한다(password=null).
4. 신규·기존 회원 관계없이 최신 Kakao access token으로 갱신한 뒤 저장한다.
5. 서비스 JWT를 발급해 반환한다.

Kakao access token은 `member.kakao_access_token` 컬럼에 저장되며, 이후 주문 완료 시 카카오 메시지 발송에 사용된다.

### 토큰 검증 및 Member 주입

`AuthenticationResolver`는 `Authorization: Bearer {token}` 헤더에서 토큰을 추출하고, JWT 서명을 검증해 이메일을 꺼낸 뒤 DB에서 Member를 조회해 반환한다. 토큰이 유효하지 않거나 헤더가 없으면 예외를 던지지 않고 null을 반환한다. 이 컴포넌트는 인증이 필요한 컨트롤러에서 `@RequestHeader("Authorization")`와 함께 직접 호출된다.

---

## 3. 데이터 모델

### DB 스키마 (V1__Initialize_project_tables.sql)

```sql
create table member
(
    id                 bigint auto_increment primary key,
    email              varchar(255) not null unique,
    password           varchar(255),
    kakao_access_token varchar(512),
    point              int          not null default 0
);
```

`password`와 `kakao_access_token`은 nullable이다. 일반 가입 회원은 `kakao_access_token`이 null이고, Kakao OAuth2 가입 회원은 `password`가 null이다.

---

## 4. 도메인 객체

auth 도메인은 독립적인 Entity를 갖지 않으며, Member Entity의 인증 관련 필드를 공유한다.

```java
// Member.java (인증 관련 필드 및 메서드)
private String email;
private String password;
private String kakaoAccessToken;

public void updateKakaoAccessToken(String kakaoAccessToken) { ... }
public String getEmail() { ... }
public String getPassword() { ... }
public String getKakaoAccessToken() { ... }
```

### 공개 API

| 메서드 | 설명 |
|--------|------|
| `AuthService.register(MemberRequest)` | 이메일/비밀번호로 회원 생성 후 JWT 반환 |
| `AuthService.login(MemberRequest)` | 이메일/비밀번호 검증 후 JWT 반환 |
| `JwtProvider.createToken(String email)` | email을 subject로 담은 서명된 JWT 생성 |
| `JwtProvider.getEmail(String token)` | JWT에서 email(subject) 추출 |
| `AuthenticationResolver.extractMember(String authorization)` | Bearer 토큰 → Member 변환, 실패 시 null 반환 |
| `KakaoLoginClient.requestAccessToken(String code)` | 인가 코드 → Kakao access token 교환 |
| `KakaoLoginClient.requestUserInfo(String accessToken)` | Kakao access token → 사용자 이메일 조회 |

---

## 5. API 명세

| HTTP | 경로 | 동작 | 요청 | 성공 응답 |
|------|------|------|------|----------|
| POST | `/api/members/register` | 일반 회원가입 | `{email, password}` | 201 + `{token}` |
| POST | `/api/members/login` | 일반 로그인 | `{email, password}` | 200 + `{token}` |
| GET | `/api/auth/kakao/login` | Kakao 로그인 시작 | 없음 | 302 리다이렉트 |
| GET | `/api/auth/kakao/callback` | Kakao OAuth2 콜백 | `?code={code}` | 200 + `{token}` |

인증 불필요: 4개 엔드포인트 모두 인증 없이 접근 가능하다.

---

## 6. DTO 설계

### 요청

`MemberRequest`는 `gift.member` 패키지에 위치하지만 `AuthService`가 직접 의존한다.

```java
// gift.member.MemberRequest
public record MemberRequest(
    @NotBlank String email,
    @NotBlank String password
) {}
```

### 응답

```java
// gift.auth.TokenResponse
public record TokenResponse(String token) {}
```

---

## 7. 다른 도메인과의 관계

```
[MemberController] ──── POST /register, /login ────► [AuthService]
                                                           │
                                                           ├──► [MemberCommandService]  (회원 생성)
                                                           ├──► [MemberQueryService]    (이메일 조회)
                                                           └──► [JwtProvider]           (JWT 발급)

[KakaoAuthController] ──► [KakaoLoginClient] ──HTTP──► Kakao API (kauth.kakao.com / kapi.kakao.com)
         │
         ├──► [MemberRepository]   (회원 조회·저장 직접 호출)
         └──► [JwtProvider]        (JWT 발급)

[AuthenticationResolver] ──► [JwtProvider]       (토큰 → email)
                         └──► [MemberRepository] (email → Member)

Member.kakaoAccessToken ──read──► [order.KakaoMessageClient]  (주문 완료 후 알림 발송)
```

- auth 도메인은 Member 도메인에 단방향으로 의존한다. Member 도메인은 auth 도메인을 참조하지 않는다.
- `KakaoAuthController`는 도메인 서비스를 거치지 않고 `MemberRepository`를 직접 사용한다.
- `member.kakao_access_token`은 auth 도메인에서 저장하고, order 도메인(`KakaoMessageClient`)에서 읽는다.
- JWT secret과 expiration은 `application.properties`(`jwt.secret`, `jwt.expiration`)로 외부화되어 있다.
