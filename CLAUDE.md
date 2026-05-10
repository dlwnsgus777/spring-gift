# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
./gradlew build          # 빌드
./gradlew test           # 전체 테스트 실행
./gradlew test --tests "gift.product.ProductControllerTest"  # 단일 테스트 클래스 실행
./gradlew bootRun        # 로컬 실행 (H2 인메모리 DB)
./gradlew ktlintFormat   # 코드 포매팅
./gradlew ktlintCheck    # 포맷 검사만
```

## Source Structure

**모든 프로덕션 코드는 `src/main/java/gift/` 하위에 Java로 작성되어 있다.** (`src/main/kotlin`은 빌드 설정만 존재하며 소스 없음)

```
src/main/java/gift/
├── auth/          # JWT 인증 + Kakao OAuth2
├── category/      # 카테고리
├── common/        # 공통 관심사 (GlobalExceptionHandler 등)
├── member/        # 회원 (포인트 시스템)
├── option/        # 상품 옵션 (SKU)
├── order/         # 주문 (재고 차감, 포인트 결제, Kakao 알림)
├── product/       # 상품
└── wish/          # 위시리스트
```

각 도메인 패키지는 `Entity`, `Controller`, `QueryService`, `CommandService`, `Repository`, `Request/Response DTO`로 구성.
새 코드 작성 시 해당 도메인 패키지 아래 Java 파일로 추가한다.

## Architecture

Spring Boot 3.5.9 기반 선물하기 커머스 플랫폼.

### Key Design Decisions

- **느슨한 FK 참조**: `Order.memberId`는 `Long` 타입 (Entity 참조 아님) — N+1 방지 의도적 설계
- **도메인 비즈니스 로직**: Entity에 직접 포함 (`Member.chargePoint()`, `Member.deductPoint()`, `Option.subtractQuantity()`)
- **DTO**: Java Record + Jakarta Validation 어노테이션 (`*Request` 클래스)
- **인증**: `AuthenticationResolver`가 JWT에서 Member를 추출해 컨트롤러 파라미터에 주입
- **Admin 분리**: `AdminProductController`, `AdminMemberController`로 관리자 API 별도 관리

### Service Layer

도메인별로 Query와 Command 서비스를 분리한다.

- **`*QueryService`**: 읽기 전용 (`@Transactional(readOnly = true)`). 도메인 객체(`Entity`)를 반환한다.
- **`*CommandService`**: 상태 변경 (`@Transactional`). 도메인 객체(`Entity`)를 반환한다.
- **`Controller`**: Service가 반환한 도메인 객체를 `*Response.from()`으로 변환해 HTTP 응답으로 내보낸다. Service가 Response DTO를 직접 반환하지 않는다.

### Exception Handling

- `gift.common.GlobalExceptionHandler` (`@RestControllerAdvice`)가 예외를 HTTP 상태로 변환한다.
- `NoSuchElementException` → 404: `findById().orElseThrow()`를 쓰고 null 체크 분기를 두지 않는다.

### Database

- **개발/테스트**: H2 인메모리
- **프로덕션**: MySQL
- **마이그레이션**: Flyway (`src/main/resources/db/migration/V*.sql`)
- **시드 데이터**: `V2__Insert_default_data.sql` — 카테고리 3개, 상품 6개, 회원 3개, 위시 4개, 옵션 8개, 주문 4개

### External Integrations

- **Kakao OAuth**: 로그인 (`KakaoLoginClient`)
- **Kakao Message**: 주문 완료 후 알림 발송 (`KakaoMessageClient`)
- **JWT**: JJWT 0.13.0, secret/expiration은 `application.properties`로 외부화

## Testing

- JUnit 5, 테스트 코드는 `src/test/java/gift/` 하위에 Java로 작성
- AAA 패턴 사용 (`// arrange`, `// act`, `// assert` 주석 필수)
- **테스트 메서드 네이밍**: `test01`, `test02`, ... 순번으로 작성하고 `@DisplayName`에 테스트 의도를 명시

### Integration Test

- `AbstractIntegrationTest`를 상속해 MySQL 컨테이너 기반 통합 테스트 작성
- 컨테이너는 싱글턴 패턴(`static { mysql.start(); }`)으로 JVM 종료 시까지 유지 — `@Testcontainers`/`@Container` 사용 시 클래스 종료마다 컨테이너가 내려가므로 사용하지 않는다
- `@DynamicPropertySource`로 DataSource를 오버라이드

### 테스트 격리 전략

테스트 클래스 유형에 따라 격리 전략이 다르다.

| 유형 | 격리 방법 | 이유 |
|------|----------|------|
| `*ControllerTest` (MockMvc) | UUID 이름으로 데이터 생성, `deleteAll` 없음 | MockMvc는 별도 스레드에서 실행되어 `@Transactional` 롤백 불가 |
| `*QueryServiceTest` | `@Transactional` — 테스트 종료 시 자동 롤백 | 서비스 직접 호출은 같은 스레드에서 실행 |
| `*CommandServiceTest` | `@Transactional` — 테스트 종료 시 자동 롤백 | 서비스 직접 호출은 같은 스레드에서 실행 |

- Controller 테스트에서 특정 데이터가 필요한 경우 `UUID.randomUUID()`를 이름에 포함해 다른 테스트와 충돌을 방지한다
- FK 제약으로 `deleteAll()`이 불가한 경우: `jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0")` 전후로 감싼다
- 서비스 테스트는 Flyway 시드 데이터를 그대로 활용한다. 시드 데이터의 존재 여부를 검증할 때는 `containsExactlyInAnyOrder` 대신 `contains`를 사용해 다른 테스트가 추가한 데이터와 충돌하지 않도록 한다
