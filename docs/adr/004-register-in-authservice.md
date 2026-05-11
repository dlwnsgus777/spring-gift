# ADR 004: 회원가입(register) 로직을 AuthService에 배치

- **상태**: 수락됨 (Accepted)
- **날짜**: 2026-05-11

---

## 맥락 (Context)

3단계 Member 도메인 서비스 추출 작업에서 `MemberController.register()`의 비즈니스 로직을 어느 서비스로 이동할지 결정해야 했다.

현재 `MemberController.register()`는 다음 세 가지를 순서대로 수행한다:

```java
// 현재 MemberController
memberRepository.existsByEmail(request.email());      // 중복 체크
memberRepository.save(new Member(email, password));   // 저장
jwtProvider.createToken(member.getEmail());           // JWT 발급 → TokenResponse 반환
```

서비스를 `auth`와 `member` 두 패키지로 분리하는 설계에서, 이 로직을 `AuthService`(auth 패키지)에 둘지 `MemberCommandService`(member 패키지)에 둘지가 쟁점이었다.

---

## 결정 (Decision)

회원가입 로직을 `AuthService`(auth 패키지)에 배치한다.

```java
// AuthService (auth 패키지)
public TokenResponse register(MemberRequest request) {
    if (memberRepository.existsByEmail(request.email())) {
        throw new IllegalArgumentException("Email is already registered.");
    }
    Member member = memberRepository.save(new Member(request.email(), request.password()));
    return new TokenResponse(jwtProvider.createToken(member.getEmail()));
}
```

`MemberCommandService`는 관리자 CRUD(create/update/delete)만 담당하고, `MemberController`는 `AuthService`만 의존한다.

---

## 고려한 대안 (Alternatives Considered)

| 대안 | 기각 이유 |
|------|----------|
| `MemberCommandService.register()` — Member 도메인 책임 | `JwtProvider`(auth)를 member 패키지에서 의존하게 되어 `auth → member` 단방향이 깨짐. 회원 저장은 수단이고 JWT 발급이 목적인데, 목적과 무관한 패키지에 배치하는 꼴 |

---

## 결과 (Consequences)

**긍정적:**
- `KakaoAuthController`(auth 패키지)가 Kakao 가입 + JWT 발급을 함께 처리하는 패턴과 일관성이 유지된다 — "가입 = auth 흐름"이라는 규칙이 생긴다
- 패키지 의존 방향이 `auth → member` 단방향으로 유지된다 (`AuthService`가 `MemberRepository`를 의존)
- `MemberCommandService`가 `JwtProvider`에 의존하지 않아 Member 도메인이 순수하게 유지된다

**부정적:**
- `AuthService`가 `MemberRepository`를 직접 의존해 auth 패키지가 member 패키지의 인프라 세부사항을 알게 된다 (추후 `MemberCommandService`를 경유하도록 변경할 여지 있음)
- 회원가입과 관리자 회원 생성이 각각 `AuthService.register()`와 `MemberCommandService.create()`로 나뉘어, 중복 이메일 체크 로직이 두 곳에 존재한다
