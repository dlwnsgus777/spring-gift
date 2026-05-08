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
├── member/        # 회원 (포인트 시스템)
├── option/        # 상품 옵션 (SKU)
├── order/         # 주문 (재고 차감, 포인트 결제, Kakao 알림)
├── product/       # 상품
└── wish/          # 위시리스트
```

각 도메인 패키지는 `Entity`, `Controller`, `Repository`, `Request/Response DTO`로 구성.  
새 코드 작성 시 해당 도메인 패키지 아래 Java 파일로 추가한다.

## Architecture

Spring Boot 3.5.9 기반 선물하기 커머스 플랫폼.

### Key Design Decisions

- **느슨한 FK 참조**: `Order.memberId`는 `Long` 타입 (Entity 참조 아님) — N+1 방지 의도적 설계
- **도메인 비즈니스 로직**: Entity에 직접 포함 (`Member.chargePoint()`, `Member.deductPoint()`, `Option.subtractQuantity()`)
- **DTO**: Java Record + Jakarta Validation 어노테이션 (`*Request` 클래스)
- **인증**: `AuthenticationResolver`가 JWT에서 Member를 추출해 컨트롤러 파라미터에 주입
- **Admin 분리**: `AdminProductController`, `AdminMemberController`로 관리자 API 별도 관리

### Database

- **개발/테스트**: H2 인메모리
- **프로덕션**: MySQL
- **마이그레이션**: Flyway (`src/main/resources/db/migration/V*.sql`)

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
- Testcontainers(`mysql:8.0`)로 컨테이너를 기동하고 `@DynamicPropertySource`로 DataSource를 오버라이드
- `@Container static` 필드로 선언해 JVM 내 모든 테스트 클래스가 컨테이너를 재사용