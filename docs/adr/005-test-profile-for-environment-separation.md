# ADR 005: 테스트 전용 프로파일로 환경 설정 분리

- **상태**: 수락됨 (Accepted)
- **날짜**: 2026-05-11

---

## 맥락 (Context)

`src/main/resources/application.properties`는 민감한 설정값을 환경변수로 외부화하고 있다.

```properties
jwt.secret=${JWT_SECRET:a-string-secret-at-least-256-bits-long}
jwt.expiration=${JWT_EXPIRATION:3600000}
kakao.login.client-id=${KAKAO_CLIENT_ID:}
kakao.login.client-secret=${KAKAO_CLIENT_SECRET:}
```

`MemberControllerTest`를 처음 실행했을 때 다음 오류가 발생했다.

```
Caused by: org.springframework.util.PlaceholderResolutionException:
  Could not resolve placeholder 'jwt.secret' in value "${jwt.secret}"
```

`JwtProvider`가 `@Value("${jwt.secret}")`로 값을 주입받는데, 테스트 실행 환경에 `JWT_SECRET` 환경변수가 없으면 Spring Framework 6.2(Spring Boot 3.5 내장)의 강화된 플레이스홀더 해석기가 예외를 던졌다. 결과적으로 `@SpringBootTest` 컨텍스트 로드 자체가 실패했다.

`AbstractIntegrationTest`를 상속하는 모든 통합 테스트 클래스가 동일한 컨텍스트를 사용하므로, 이 문제는 `JwtProvider` 빈을 실제로 사용하지 않는 `CategoryControllerTest`에도 잠재적으로 영향을 미친다.

---

## 결정 (Decision)

`src/test/resources/application-test.properties`를 신설하고, `AbstractIntegrationTest`에 `@ActiveProfiles("test")`를 추가해 테스트 실행 시 `test` 프로파일을 활성화한다.

```properties
# src/test/resources/application-test.properties
jwt.secret=test-secret-that-is-at-least-256-bits-long-for-testing-purpose
jwt.expiration=3600000

kakao.login.client-id=test-client-id
kakao.login.client-secret=test-client-secret
kakao.login.redirect-uri=http://localhost:8080/api/auth/kakao/callback
```

```java
// AbstractIntegrationTest.java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest { ... }
```

`application-test.properties`는 환경변수 의존 없이 하드코딩된 값만 담는다. `application.properties`의 나머지 설정(spring.application.name 등)은 그대로 상속된다.

---

## 고려한 대안 (Alternatives Considered)

| 대안 | 기각 이유 |
|------|----------|
| `src/test/resources/application.properties`로 전체 덮어쓰기 | 필요한 값(JWT, Kakao)만 오버라이드하면 되는데 main 설정 전체를 테스트 파일에 중복 관리하게 됨. 이후 main 설정 변경 시 테스트 파일도 수동으로 동기화해야 하는 유지보수 부담 발생 |
| 환경변수 설정을 강제하는 CI 가이드 문서화 | 로컬 환경마다 설정 여부가 달라 재현성이 없음. 새 개발자 온보딩 시 누락 가능성 있음 |

---

## 결과 (Consequences)

**긍정적:**
- 환경변수 유무에 관계없이 테스트가 항상 동일하게 실행됨 — 재현성 보장
- 필요한 값만 오버라이드하므로 `application.properties`와 중복이 없음
- `AbstractIntegrationTest`를 상속하는 모든 현재·미래 테스트 클래스에 자동 적용

**부정적:**
- 테스트용 JWT secret이 평문으로 리포지터리에 커밋됨 — 테스트 전용 더미값이므로 보안 리스크는 없지만, 실제 secret과 혼동하지 않도록 주의 필요
- `@ActiveProfiles("test")` 추가로 컨텍스트 캐시 키가 변경됨 — 이전에 `test` 프로파일 없이 캐시된 컨텍스트가 있다면 재로드 필요 (일회성)
