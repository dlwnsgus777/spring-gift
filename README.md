# spring-gift

선물하기 커머스 플랫폼

---

## 구현 목표

카카오 선물하기 서비스를 모티브로 한 커머스 백엔드의 **레거시 코드를 안전하게 리팩터링**하는 것이 목표다.

단순히 새 기능을 추가하는 것이 아니라, 기존 동작을 통합 테스트로 먼저 고정한 뒤 구조를 바꾸는 방식으로 진행한다.
구조 변경과 작동 변경을 한 커밋에 섞지 않고, 작동 변경은 반드시 테스트로 변경 전후를 증명한다.

- **Controller 의존성 문제**: Controller가 Repository를 직접 참조하는 구조에서 Service 계층을 추출해 HTTP와 비즈니스 로직을 분리한다.
- **누락된 트랜잭션**: 재고 차감 후 포인트 부족 예외 발생 시 재고가 차감된 채로 남는 버그를 `@Transactional`로 수정하고 테스트로 증명한다.
- **도메인 검증 책임**: Controller/Service에 흩어진 검증 로직을 Entity 생성자로 이동해 잘못된 상태로 객체가 만들어지는 것을 원천 차단한다.
- **동시성 처리**: 재고 차감의 Race Condition을 Pessimistic Lock(`SELECT FOR UPDATE`)으로 해결하고 동시 주문 테스트로 증명한다.

### 핵심 목표

| 목표 | 설명 |
|------|------|
| 자동화된 테스트 환경 | Testcontainers + MySQL로 누구나 동일하게 실행 가능한 통합 테스트 환경 구축 |
| 구조 변경 (작동 불변) | 스타일 정리, 불필요한 코드 제거, Controller → Service 계층 추출 — 기존 동작 유지 |
| 작동 변경 (증거 필수) | 트랜잭션 경계 설정, 누락 기능 구현, 도메인 책임 이동 — 테스트로 변경 전후 증명 |
| Kakao OAuth2 연동 | 카카오 로그인 + 주문 완료 알림 메시지 발송 |

### 진행 원칙

- **TDD 루프 유지**: Red → Green → Refactor. 구조 변경과 작동 변경은 한 커밋에 섞지 않는다.
- **기능 단위 커밋**: `git diff`를 보고 30초 내에 의도를 설명할 수 있어야 한다.
- **ADR 작성**: 선택지가 2개 이상이고 트레이드오프가 존재하는 결정은 반드시 기록한다.
- **AI 활용 기록**: 사용한 프롬프트, 접근 방법, 학습 내용을 README에 남긴다.

### 도메인 구성

`Category` → `Member` → `Point` → `Product` → `Option` → `Wish` → `Order` → `KakaoAuth`

---

## 구현 요구사항 체크리스트

### 1단계: 테스트 환경 구축 (공통 인프라) — [구현 계획](docs/plan/task-step1-test-environment.md)

> 누구나 동일하게 실행 가능하고, 프로덕션과 유사한 환경에서 테스트할 수 있는 자동화된 테스트 환경

- [x] Testcontainers 의존성 추가 (`build.gradle.kts`)
- [x] MySQL 컨테이너 기반 통합 테스트 설정 (`@SpringBootTest` + `@Testcontainers`)
- [x] 공통 테스트 베이스 클래스 작성 (`AbstractIntegrationTest`)
- [x] Flyway 마이그레이션이 테스트 컨테이너에 자동 적용되는지 확인

---

### 2단계: Category 도메인 — [구현 계획](docs/plan/task-step2-category-domain.md)

> 가장 단순한 CRUD — 도메인별 패턴을 처음 적용하는 출발점

- [x] `CategoryController` API 통합 테스트 작성 (`AbstractIntegrationTest` 상속)
- [x] `CategoryController` 스타일 정리 — `orElse(null)` → Optional 패턴
- [x] `CategoryService` 추출 — Controller에서 Repository 의존성 분리 (`CategoryQueryService` / `CategoryCommandService` 분리)

---

### 3단계: Member 도메인 — [구현 계획](docs/plan/task-step3-member-domain.md)

> 회원 등록·로그인 로직 — 인증 흐름을 도메인 계층으로 분리

- [x] `MemberController`, `AdminMemberController` API 통합 테스트 작성
- [x] `MemberController` 스타일 정리 — 의미 없는 클래스 레벨 Javadoc 제거
- [x] `MemberService` 추출 — 등록·로그인 비즈니스 로직 분리

---

### 4단계: Point 도메인 — [구현 계획](docs/plan/task-step4-point-domain.md)

> Member에서 분리된 포인트 충전 로직 — 별도 패키지로 책임 이동

- [x] `Point` `@Embeddable` VO 추출 — `gift/point` 패키지에 신규 생성, `Member.chargePoint()`·`deductPoint()`를 Point에 위임 (ADR 006)

---

### 5단계: Product 도메인 — [구현 계획](docs/plan/task-step5-product-domain.md)

> 검증 로직 + 2개 Repository — 도메인 책임 이동 첫 적용

- [x] `ProductController`, `AdminProductController` API 통합 테스트 작성
- [x] `ProductController` 스타일 정리 — `orElse(null)` → Optional 패턴 (4곳)
- [x] `ProductService` 추출 — `validateName()`, `CategoryRepository` 직접 조회 분리
- [x] `ProductNameValidator.validate()` → `Product` 생성자 내부 검증으로 이동
- [x] `Product` 단위 테스트 — Entity 레벨 불변식 검증

---

### 6단계: Option 도메인 — [구현 계획](docs/plan/task-step6-option-domain.md)

> 비즈니스 규칙(최소 1개 옵션) + 검증 로직 — 도메인 책임 이동 심화

- [x] `OptionController` API 통합 테스트 작성
- [x] `OptionController` 스타일 정리 — `orElse(null)` → Optional 패턴, `Collectors.toList()` → `.toList()`
- [x] `OptionService` 추출 — `validateName()`, 중복 체크 로직 분리 (`OptionQueryService` / `OptionCommandService`)
- [x] `OptionNameValidator.validate()` → `Option` 생성 시점 검증으로 이동
- [x] `Option` 단위 테스트 — Entity 레벨 불변식 검증 (`validateDeletion()` 포함)

---

### 7단계: Wish 도메인 — [구현 계획](docs/plan/task-step7-wish-domain.md)

> 인증 + 중복 체크 + 2개 Repository

- [x] `WishController` API 통합 테스트 작성
- [x] `WishController` 스타일 정리 — `// check auth`, `// check product`, `// check duplicate` 흐름 주석 제거, `var` → 타입 명시, `orElse(null)` → Optional 패턴
- [x] `WishService` 추출 — Controller에서 Repository 의존성 분리 (`WishQueryService` / `WishCommandService` 분리, `AuthService.extractMember()` 추가)

---

### 8단계: Order 도메인 — [구현 계획](docs/plan/task-step8-order-domain.md)

> 가장 복잡 — 5개 Repository, 트랜잭션, 누락 기능, 도메인 책임

- [x] `OrderController` API 통합 테스트 작성
- [x] `OrderController` 스타일 정리 — 흐름 주석 블록 제거 (`// auth check`, `// validate option` 등 7곳), `var` → 타입 명시, `orElse(null)` → Optional 패턴
- [x] `OrderService` 추출 — `OrderQueryService` / `OrderCommandService` / `NotifySendService` 분리, Controller에서 Repository 의존성 제거
- [x] `OrderCommandService.createOrder()` `@Transactional` 적용 — 재고 차감 + 포인트 차감 + 주문 저장 + 위시 삭제를 단일 트랜잭션으로
- [x] 트랜잭션 경계 검증: 재고 차감 후 포인트 부족 예외 발생 시 재고가 롤백되는지 테스트로 증명 (test04)
- [x] 위시리스트 자동 삭제 구현 — 주문한 상품이 위시리스트에 있으면 자동 삭제 (`WishCommandService.deleteByMemberIdAndProductId()`)
- [x] 위시리스트 자동 삭제 전후 동작 차이를 테스트로 증명 (주문 후 위시에서 사라짐)
- [x] 포인트 충분 여부 확인 — `Member.deductPoint()` 내부 예외로 처리
- [x] 재고 동시성 처리 — Pessimistic Lock (`SELECT FOR UPDATE`) 적용, 동시 주문 테스트로 증명 (test06)

---

### 9단계: 코드 정리

> 전체 도메인 완료 후 공통 코드 품질 개선

- [x] `support` 패키지 생성 — `UUIDGenerator.uuid()` static 메서드로 테스트 전체의 uuid 생성 중복 제거
- [x] 엔티티별 Fixture 클래스 생성 (빌더 패턴) — `MemberFixture` / `CategoryFixture` / `ProductFixture` / `OptionFixture` / `WishFixture` / `OrderFixture`
- [x] 전체 테스트 파일에서 `new Entity(...)` 직접 생성을 Fixture 빌더로 교체 (17개 파일)
- [x] `gift.notification` 패키지 분리 — `MessageClient` / `KakaoMessageClient` / `NotifySendService` / `FakeMessageClient`를 `gift.order`에서 독립 패키지로 이동
- [x] 미사용 클래스 제거 — `AuthenticationResolver` (`AuthService.extractMember()`로 완전 대체)
- [x] 불필요한 `@Autowired` 및 import 제거 — 단일 생성자는 Spring이 자동 주입 (`JwtProvider`, `MemberController`, `AdminMemberController`)
- [x] what 주석 제거 — `JwtProvider` Javadoc 3개, `Order` / `Wish`의 `// primitive FK` 주석

---

### 10단계: KakaoAuth 도메인 — [구현 계획](docs/plan/task-step10-kakao-auth-domain.md)

> Kakao OAuth2 콜백 로직 — auth 패키지 서비스 계층 완성

- [x] `KakaoAuthController` API 통합 테스트 작성 — `/api/auth/kakao/login` 리다이렉트 302, `/api/auth/kakao/callback` 흐름 테스트
- [x] `KakaoAuthService` 추출 — `callback()` 로직(토큰 교환 → 회원 조회/생성 → JWT 발급) 분리, Controller의 `MemberRepository` · `JwtProvider` 직접 의존 제거

---

## 구현 전략

### 핵심 원칙: 도메인별 수직 슬라이스

```
공통 인프라(1단계) → 도메인별 [테스트 → 스타일 → 서비스 추출 → 도메인 개선]
```

각 도메인을 통합 테스트 작성부터 서비스 추출까지 완결한 뒤 다음 도메인으로 넘어간다.
도메인이 완결될 때마다 테스트가 Green이면 회귀 없음이 보장된다.

---

### 도메인 진행 순서 (복잡도 오름차순)

| 단계 | 도메인 | 핵심 작업 |
|------|--------|-----------|
| 2 | Category | 단순 CRUD — 패턴 적용 출발점 |
| 3 | Member | 등록·로그인 로직 분리 |
| 4 | Point | `MemberCommandService.chargePoint()` → `gift/point` 패키지 분리 |
| 5 | Product | `ProductNameValidator` → `Product` 생성자 이동 |
| 6 | Option | `OptionNameValidator` → `Option` 생성자 이동 + 비즈니스 규칙 |
| 7 | Wish | 인증 + 중복 체크 흐름 정리 |
| 8 | Order | 트랜잭션 + 누락 기능 + 가장 복잡한 흐름 |
| 9 | 코드 정리 | 전체 도메인 완료 후 공통 코드 품질 개선 |
| 10 | KakaoAuth | Kakao OAuth2 콜백 서비스 추출 |

---

### 각 도메인 공통 패턴

```
1. 통합 테스트 작성  →  현재 API 동작을 Green 테스트로 고정
2. 스타일 정리      →  의미 없는 주석, var 타입 명시, Optional 패턴
3. Service 추출    →  Controller는 HTTP, Service는 비즈니스
4. 도메인 개선      →  (해당 도메인에 적용되는 항목만)
```

**스타일 정리 원칙**: "what"을 반복하는 주석은 제거, "why"(동작 특성·의도)를 설명하는 주석은 유지.

**Service 추출 패턴**:
```
Controller 메서드 → Service 메서드로 코드 이동
Controller는 Service 호출 + HTTP 응답 변환만 남김
통합 테스트로 응답 스펙 동일함 확인
```

---

### Order 도메인 특수 작업

**트랜잭션 경계 증명:**
```
[before] 재고 차감 후 포인트 부족 예외 → 재고가 차감된 채로 남음 (버그)
[after]  재고 차감 후 포인트 부족 예외 → 재고 롤백됨 (수정됨)
```
`@Transactional` 추가 → 해당 시나리오 테스트가 Green → 변경 증거

**위시리스트 자동 삭제:**
```
1. "주문 후 위시리스트가 삭제된다" 테스트 작성 → Red
2. WishRepository.deleteByMemberIdAndProductId() 구현
3. OrderService에서 호출
4. 테스트 Green → 구현 완료 증거
```

---

### 도메인 검증 책임 이동 (5, 6단계)

검증 로직이 정적 유틸이나 Controller 분기문에 있으면, 도메인 객체가 잘못된 상태로 생성될 수 있다.

```java
// before: Controller/Service에서 검증 후 생성
List<String> errors = ProductNameValidator.validate(name);
if (!errors.isEmpty()) throw ...;
new Product(name, ...);

// after: 생성자에서 불변식 보장
new Product(name, ...); // 내부에서 검증, 위반 시 예외
```

---

## HTTP 테스트 파일

IntelliJ HTTP Client로 API를 직접 테스트할 수 있는 `.http` 파일 모음. `http/` 디렉토리에 도메인별로 구성되어 있다.

### 파일 목록

| 파일 | 도메인 | 주요 요청 |
|------|--------|-----------|
| `http/auth.http` | 인증 | 카카오 로그인, 회원가입, 로그인 (토큰 자동 저장) |
| `http/category.http` | 카테고리 | 조회·생성·수정·삭제 |
| `http/product.http` | 상품 | 조회(페이징)·생성·수정·삭제 |
| `http/option.http` | 옵션 | 상품별 옵션 조회·생성·삭제 |
| `http/wish.http` | 위시리스트 | 조회·추가·제거 (JWT 필요) |
| `http/order.http` | 주문 | 목록 조회·생성 (JWT 필요) |
| `http/member.http` | 회원 (관리자) | 전체 조회·포인트 충전·삭제 |

### 사용 방법

1. `http/http-client.env.json`의 `local` 환경이 기본 설정 (`baseUrl`: `http://localhost:8080`)
2. IntelliJ `.http` 파일 편집기 우측 상단 드롭다운에서 **`local`** 선택
3. `auth.http`에서 로그인 실행 → `token`이 환경 변수에 자동 저장
4. 이후 JWT가 필요한 요청(`wish.http`, `order.http`)에서 `{{token}}`이 자동으로 채워짐

---

## AI 도구 활용 기록

| 프롬프트 | 시도한 내용 | 관련 문서 |
|----------|-------------|-----------|
| 구현 요구사항을 분석해서 README에 체크리스트와 구현 전략을 작성해줘 | 프로젝트 코드 전체를 분석하여 6단계 체크리스트와 단계별 구현 전략 작성 | [세션 문서](docs/ai-sessions/2026-05-07.md) |
| 계획서 기반 Testcontainers MySQL 통합 테스트 환경 구축 | build.gradle.kts 정리, AbstractIntegrationTest·FlywayMigrationTest 작성, 테스트 Green 확인 | [세션 문서](docs/ai-sessions/2026-05-08.md) |
| 구현 전략을 도메인별 수직 슬라이스로 변경해줘 | 관심사 횡단 6단계 → 도메인별 7단계로 README 재편성, ADR 002 작성 | [세션 문서](docs/ai-sessions/2026-05-09.md) |
| Category 도메인 정리 — Query/Command 서비스 분리 + 병렬 테스트 격리 | GlobalExceptionHandler 추가, CategoryQueryService/CategoryCommandService TDD 구현, 싱글턴 컨테이너 패턴 적용 | [세션 문서](docs/ai-sessions/2026-05-10.md) |
| Member 도메인 3단계 — 통합 테스트 + AuthService/MemberQueryService/MemberCommandService TDD 추출 | 테스트 환경 분리(ADR 005), 서비스 3개 TDD 구현, 컨트롤러 리팩터링, dirty checking으로 불필요한 save() 제거 | [세션 문서](docs/ai-sessions/2026-05-11.md) |
| Point 도메인 4단계 — Point @Embeddable VO TDD 구현, PointCommandService 시도 후 설계 검토로 제거 결정 | README 순서 재편성, 도메인 분석·계획서 작성, Point VO TDD, PointCommandService 설계 문제 발견·철회, ADR 006 작성 | [세션 문서](docs/ai-sessions/2026-05-11-2.md) |
| Product 도메인 5단계 — Query/Command 서비스 분리, 생성자 검증 이동, Admin 카카오 허용 방식 결정 | 도메인 분석·계획서 작성, ProductController/AdminProductController 통합 테스트, ProductQueryService/ProductCommandService TDD, Product 생성자 검증 이동, 미사용 코드 정리 | [세션 문서](docs/ai-sessions/2026-05-12.md) |
| Option 도메인 6단계 — TDD 진행 중 OptionCommandService 구조 문제 발견, Product를 애그리게이트 루트로 전환 | 도메인 분석·계획서 작성, OptionControllerTest TDD, 애그리게이트 루트 설계 변경 (OptionCommandService 삭제, Product.addOption/removeOption 추가), ADR 007 작성 | [세션 문서](docs/ai-sessions/2026-05-13.md) |
| Wish 도메인 7단계 — TDD 7사이클로 WishController Repository 의존 제거, UnauthorizedException·ForbiddenException 추가 | 도메인 분석·계획서 작성, WishQueryService/WishCommandService TDD, AuthService.extractMember 추가, WishController 교체, tdd-team 스킬 체크포인트 강화 | [세션 문서](docs/ai-sessions/2026-05-14.md) |
| Order 도메인 8단계 — TDD 7사이클로 OrderController Repository 의존 제거, 트랜잭션 경계 증명, Pessimistic Lock 동시성 처리 | 도메인 분석·계획서 작성, OrderQueryService/OrderCommandService/NotifySendService TDD, FakeMessageClient 테스트 더블, findByIdWithLock SELECT FOR UPDATE 적용, ADR 008 작성 | [세션 문서](docs/ai-sessions/2026-05-14-2.md) |
| 9단계 코드 정리 — support 패키지·Fixture 도입, notification 패키지 분리, 미사용 코드·주석 제거 | UUIDGenerator + 6개 Fixture 클래스 생성, 17개 테스트 파일 교체, gift.notification 패키지 신설, AuthenticationResolver 삭제, @Autowired·Javadoc·what 주석 제거 | [세션 문서](docs/ai-sessions/2026-05-14-3.md) |
| KakaoAuth 도메인 10단계 — 계획서 작성 + TDD 11사이클로 KakaoAuthService 추출, KakaoTokenResponse·KakaoUserResponse top-level 분리 | 계획서 작성(FakeKakaoLoginClient 상속 방식), KakaoAuthControllerTest/KakaoAuthServiceTest TDD, MemberQueryService·MemberCommandService 확장, Controller 리팩터링 | [세션 문서](docs/ai-sessions/2026-05-14-4.md) |
| 현재 코드를 파악해서 docs/domain/ 내부 문서들을 최신화해줘 | 9개 핵심 소스 파일 분석, 6개 기존 도메인 문서 수정 + notification_domain.md 신규 작성 (코드-문서 불일치 7곳 해소) | [세션 문서](docs/ai-sessions/2026-05-15.md) |
| 각 API를 테스트할 수 있도록 http 파일 만들어줘, 각 도메인별로 | http/ 디렉토리에 도메인별 .http 파일 7개 + 환경변수 파일 생성, README.md에 HTTP 테스트 파일 섹션 추가 | [세션 문서](docs/ai-sessions/2026-05-15-2.md) |

---

## AI 스킬 목록

이 프로젝트에서 사용 가능한 Claude Code 스킬. 트리거 키워드를 프롬프트에 포함하면 자동으로 실행된다.

### 프로젝트 전용 스킬 (`.claude/skills/`)

| 스킬 | 트리거 키워드 예시 | 설명 |
|------|-------------------|------|
| `tdd-team` | "TDD로 개발해줘", "TDD 시작" | Red→Green→Refactor 3단계를 에이전트 팀이 순차 실행. 실패 테스트 작성 → 최소 구현 → 리팩터링 사이클을 자동화 |
| `plan-maker` | "계획 작성해줘", "구현 계획 만들어줘" | `docs/plan/` 하위에 구조화된 구현 계획 마크다운 문서 생성. 체크리스트, 단계별 작업, 기술 결정 포함 |
| `adr-writer` | "ADR 남겨줘", "결정사항 문서화해줘" | 대화에서 아키텍처 결정을 추출해 `docs/adr/` 에 번호 붙은 ADR 파일 생성. 채택 이유와 포기한 대안 기록 |
| `domain-analyzer` | "도메인 분석해줘", "도메인 정리해줘" | 도메인 패키지 소스와 Flyway 마이그레이션을 읽어 `docs/domain/` 에 비즈니스 관점 문서 생성 |
| `ai-work-summary` | "ai 작업 요약해줘", "오늘 작업 정리해줘" | git 변경 사항과 대화 내용을 분석해 `docs/ai-sessions/` 에 세션 요약 문서 생성. README AI 활용 기록 자동 업데이트 |

---

## 코드 리뷰

| 리뷰 항목 | 계획 문서 |
|----------|---------|
| Not Found 로깅 및 orElseThrow 메시지 통일 | [구현 계획](docs/plan/task-not-found-logging.md) |

---

## 현재 상태 요약

| 항목 | 현재 상태 |
|------|-----------|
| 테스트 환경 | Testcontainers MySQL 컨테이너 + Flyway 마이그레이션 검증 완료 |
| Service 계층 | 전 도메인 완료 — Category / Member / Product / Option / Wish / Order / KakaoAuth |
| 트랜잭션 | 전 도메인 `@Transactional` 적용 완료. `OrderCommandService.createOrder()`는 재고 차감·포인트 차감·주문 저장·위시 삭제를 단일 트랜잭션으로 처리 |
| 예외 처리 | `GlobalExceptionHandler`로 일원화 (`NoSuchElementException` → 404, `IllegalArgumentException` → 400, `UnauthorizedException` → 401, `ForbiddenException` → 403) |
| 동시성 처리 | `Option` 재고에 Pessimistic Lock(`SELECT FOR UPDATE`) 적용 완료 |
| 위시리스트 자동 삭제 | 구현 완료 — 주문 시 해당 상품이 위시리스트에 있으면 자동 삭제 |
| 도메인 검증 | `Product`·`Option` 생성자 내부 검증으로 이동 완료 |
| HTTP 테스트 | `http/` 디렉토리에 도메인별 `.http` 파일 7개 구성 완료 |
