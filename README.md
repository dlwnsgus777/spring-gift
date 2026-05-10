# spring-gift

선물하기 커머스 플랫폼 — Spring Boot 3.5 기반 백엔드 학습 프로젝트

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

### 3단계: Member 도메인

> 회원 등록·로그인 로직 — 인증 흐름을 도메인 계층으로 분리

- [ ] `MemberController`, `AdminMemberController` API 통합 테스트 작성
- [ ] `MemberController` 스타일 정리 — 의미 없는 클래스 레벨 Javadoc 제거
- [ ] `MemberService` 추출 — 등록·로그인 비즈니스 로직 분리

---

### 4단계: Product 도메인

> 검증 로직 + 2개 Repository — 도메인 책임 이동 첫 적용

- [ ] `ProductController`, `AdminProductController` API 통합 테스트 작성
- [ ] `ProductController` 스타일 정리 — `orElse(null)` → Optional 패턴 (4곳)
- [ ] `ProductService` 추출 — `validateName()`, `CategoryRepository` 직접 조회 분리
- [ ] `ProductNameValidator.validate()` → `Product` 생성자 내부 검증으로 이동
- [ ] `Product` 단위 테스트 — Entity 레벨 불변식 검증

---

### 5단계: Option 도메인

> 비즈니스 규칙(최소 1개 옵션) + 검증 로직 — 도메인 책임 이동 심화

- [ ] `OptionController` API 통합 테스트 작성
- [ ] `OptionController` 스타일 정리 — `orElse(null)` → Optional 패턴, `Collectors.toList()` → `.toList()`
- [ ] `OptionService` 추출 — `validateName()`, 중복 체크 로직 분리
- [ ] `OptionNameValidator.validate()` → `Option` 생성 시점 검증으로 이동
- [ ] `Option` 단위 테스트 — Entity 레벨 불변식 검증

---

### 6단계: Wish 도메인

> 인증 + 중복 체크 + 2개 Repository

- [ ] `WishController` API 통합 테스트 작성
- [ ] `WishController` 스타일 정리 — `// check auth`, `// check product`, `// check duplicate` 흐름 주석 제거, `var` → 타입 명시, `orElse(null)` → Optional 패턴
- [ ] `WishService` 추출 — Controller에서 Repository 의존성 분리

---

### 7단계: Order 도메인

> 가장 복잡 — 5개 Repository, 트랜잭션, 누락 기능, 도메인 책임

- [ ] `OrderController` API 통합 테스트 작성
- [ ] `OrderController` 스타일 정리 — 흐름 주석 블록 제거 (`// auth check`, `// validate option` 등 7곳), `var` → 타입 명시, `orElse(null)` → Optional 패턴
- [ ] `OrderService` 추출 — Controller에서 5개 Repository 의존성 제거
- [ ] `OrderService.createOrder()` `@Transactional` 적용 — 재고 차감 + 포인트 차감 + 주문 저장을 단일 트랜잭션으로
- [ ] 트랜잭션 경계 검증: 재고 차감 후 포인트 부족 예외 발생 시 재고가 롤백되는지 테스트로 증명
- [ ] 위시리스트 자동 삭제 구현 — 주문한 상품이 위시리스트에 있으면 자동 삭제 (`WishRepository.findByMemberIdAndProductId()` 활용)
- [ ] 위시리스트 자동 삭제 전후 동작 차이를 테스트로 증명 (주문 후 위시에서 사라짐)
- [ ] 포인트 충분 여부 확인 — `Member.deductPoint()` 내부 예외로 처리

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
| 4 | Product | `ProductNameValidator` → `Product` 생성자 이동 |
| 5 | Option | `OptionNameValidator` → `Option` 생성자 이동 + 비즈니스 규칙 |
| 6 | Wish | 인증 + 중복 체크 흐름 정리 |
| 7 | Order | 트랜잭션 + 누락 기능 + 가장 복잡한 흐름 |

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

### 도메인 검증 책임 이동 (4, 5단계)

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

## AI 도구 활용 기록

| 프롬프트 | 시도한 내용 | 관련 문서 |
|----------|-------------|-----------|
| 구현 요구사항을 분석해서 README에 체크리스트와 구현 전략을 작성해줘 | 프로젝트 코드 전체를 분석하여 6단계 체크리스트와 단계별 구현 전략 작성 | [세션 문서](docs/ai-sessions/2026-05-07.md) |
| 계획서 기반 Testcontainers MySQL 통합 테스트 환경 구축 | build.gradle.kts 정리, AbstractIntegrationTest·FlywayMigrationTest 작성, 테스트 Green 확인 | [세션 문서](docs/ai-sessions/2026-05-08.md) |
| 구현 전략을 도메인별 수직 슬라이스로 변경해줘 | 관심사 횡단 6단계 → 도메인별 7단계로 README 재편성, ADR 002 작성 | [세션 문서](docs/ai-sessions/2026-05-09.md) |
| Category 도메인 정리 — Query/Command 서비스 분리 + 병렬 테스트 격리 | GlobalExceptionHandler 추가, CategoryQueryService/CategoryCommandService TDD 구현, 싱글턴 컨테이너 패턴 적용 | [세션 문서](docs/ai-sessions/2026-05-10.md) |

---

## 현재 상태 요약

| 항목 | 현재 상태 |
|------|-----------|
| 테스트 환경 | Testcontainers MySQL 컨테이너 + Flyway 마이그레이션 검증 완료 |
| Service 계층 | 없음 (Controller가 Repository 직접 사용) |
| 트랜잭션 | 미적용 (`@Transactional` 없음) |
| 위시리스트 자동 삭제 | 미구현 (Order 흐름 주석에 의도만 존재) |
| 도메인 검증 | 외부 유틸 클래스에 위치 (`ProductNameValidator`, `OptionNameValidator`) |
