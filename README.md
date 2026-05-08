# spring-gift

선물하기 커머스 플랫폼 — Spring Boot 3.5 기반 백엔드 학습 프로젝트

---

## 구현 요구사항 체크리스트

### 1단계: 테스트 환경 구축 — [구현 계획](docs/plan/task-step1-test-environment.md)

> 누구나 동일하게 실행 가능하고, 프로덕션과 유사한 환경에서 테스트할 수 있는 자동화된 테스트 환경

- [ ] Testcontainers 의존성 추가 (`build.gradle.kts`)
- [ ] MySQL 컨테이너 기반 통합 테스트 설정 (`@SpringBootTest` + `@Testcontainers`)
- [ ] 공통 테스트 베이스 클래스 작성 (`AbstractIntegrationTest`)
- [ ] Flyway 마이그레이션이 테스트 컨테이너에 자동 적용되는지 확인
- [ ] GitHub Actions CI 설정 — 도커 없이도 동일한 테스트가 실행됨을 확인

---

### 2단계: 스타일 정리 _(작동 변경 없음)_

> 불필요한 코드 제거 — 기능은 그대로, 읽기 쉬운 코드로

- [ ] `OrderController` 내 의미 없는 흐름 주석 제거 (`// auth check`, `// validate option` 등)
- [ ] Controller에서 사용하지 않는 import 제거
- [ ] `null` 반환 대신 `Optional` 사용 패턴으로 통일 (Repository 조회 부분)
- [ ] 일관성 없는 변수명 정리 (`var` 혼용 등)

---

### 3단계: 서비스 계층 추출 _(구조 변경, 작동 변경 없음)_

> Controller가 Repository를 직접 다루는 구조 → Controller는 HTTP, Service는 비즈니스

- [ ] `OrderService` 추출 — Controller에서 5개 Repository 의존성 제거
- [ ] `ProductService` 추출 — `validateName()`, `CategoryRepository` 직접 조회 분리
- [ ] `WishService` 추출
- [ ] `CategoryService` 추출
- [ ] `OptionService` 추출 — `OptionNameValidator`, 중복 체크 로직 분리
- [ ] `MemberService` 추출 — 회원 등록/로그인 로직 분리
- [ ] 각 Service 추출 후 기존 동작 변화 없음을 통합 테스트로 검증

---

### 4단계: 트랜잭션 경계 세우기 _(작동 변경)_

> DB 작업 묶음은 원자적으로 — 중간 실패 시 전체 롤백

- [ ] `OrderService.createOrder()` — 재고 차감 + 포인트 차감 + 주문 저장을 단일 트랜잭션으로 묶기 (`@Transactional`)
- [ ] 트랜잭션 경계 검증: 재고 차감 후 포인트 부족 예외 발생 시 재고가 롤백되는지 테스트로 증명
- [ ] 읽기 전용 메서드에 `@Transactional(readOnly = true)` 적용

---

### 5단계: 누락된 작동 구현 _(작동 변경)_

> 코드에 명시된 의도대로 실제 동작하도록

- [ ] `OrderController.createOrder()`의 `// cleanup wish` 단계 실제 구현
  - 주문한 상품이 위시리스트에 있으면 자동으로 삭제
  - `WishRepository.findByMemberIdAndProductId()` 활용
- [ ] 누락된 작동 구현 전후 동작 차이를 테스트로 증명 (위시리스트 포함 주문 → 주문 후 위시에서 사라짐)

---

### 6단계: 도메인 책임 되찾기 _(작동 변경)_

> 비즈니스 로직은 도메인 객체 안으로

- [ ] `ProductNameValidator.validate()` — 정적 유틸에서 `Product` 생성/수정 시 자동 검증으로 이동
- [ ] `OptionNameValidator.validate()` — `Option` 생성 시점에 검증 책임 이전
- [ ] 포인트 충분 여부 확인 로직 — Controller/Service 분기문 → `Member.deductPoint()` 내부 예외로 처리
- [ ] 도메인 로직 이동 후 단위 테스트로 Entity 레벨 동작 검증

---

## 구현 전략

### 핵심 원칙: 안전한 순서

```
테스트 환경 구축 → 리팩터링 → 작동 변경
```

테스트가 없는 상태에서 구조를 바꾸면 회귀를 감지할 수 없다.
반드시 테스트 환경을 먼저 구축하고, 구조 변경(2~3단계)으로 안전망을 만든 후,
작동 변경(4~6단계)을 수행한다.

---

### 1단계 전략: Testcontainers로 재현 가능한 환경

**문제:** 현재 테스트가 전혀 없고, 개발 환경은 H2(인메모리)이나 프로덕션은 MySQL.
H2와 MySQL은 동작 차이가 있어 H2 테스트는 거짓 안전망을 제공한다.

**해결:**
```
Testcontainers(MySQL 컨테이너) + Flyway 자동 적용
→ 누구든 docker만 있으면 동일한 환경에서 테스트 실행
→ CI에서도 동일하게 실행
```

**베이스 클래스 패턴:**
```java
@SpringBootTest
@Testcontainers
abstract class AbstractIntegrationTest {
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");
    // DataSource를 컨테이너로 오버라이드
}
```

---

### 2단계 전략: 스타일 정리는 최소 변경

의미 없는 주석, 사용하지 않는 import만 제거한다.
로직 변경 없이 diff가 순수하게 삭제만 있어야 한다.
테스트가 그대로 통과하면 성공.

---

### 3단계 전략: 서비스 추출 우선순위

복잡도가 높은 순서로 추출한다:

1. **`OrderService`** — 5개 Repository 의존, 가장 복잡한 흐름
2. **`ProductService`** — 검증 로직 + Category 조회
3. 나머지 도메인 (`Wish`, `Option`, `Member`, `Category`)

**추출 패턴:**
```
Controller 메서드 → Service 메서드로 코드 이동
Controller는 Service 호출만 남김
통합 테스트로 응답 스펙 동일함 확인
```

---

### 4단계 전략: 트랜잭션은 테스트로 증명

트랜잭션 추가는 동작을 변경한다. 추가 전후를 테스트로 비교한다.

**증명 방법:**
```
[before] 재고 차감 후 포인트 부족 예외 → 재고가 차감된 채로 남음 (버그)
[after]  재고 차감 후 포인트 부족 예외 → 재고 롤백됨 (수정됨)
```
`@Transactional` 추가 → 해당 시나리오 테스트가 Green → 변경 증거

---

### 5단계 전략: 누락 기능은 테스트 먼저

`// cleanup wish` 주석만 있고 실제 코드가 없는 상황.

```
1. "주문 후 위시리스트가 삭제된다" 테스트 작성 → Red
2. WishRepository.deleteByMemberIdAndProductId() 구현
3. OrderService에서 호출
4. 테스트 Green → 구현 완료 증거
```

---

### 6단계 전략: 도메인 책임 이동

검증 로직이 정적 유틸(`ProductNameValidator`)이나 Service 분기문에 있으면,
도메인 객체가 잘못된 상태로 생성될 수 있다.

**목표 상태:**
```java
// before: Controller/Service에서 검증 후 생성
List<String> errors = ProductNameValidator.validate(name);
if (!errors.isEmpty()) throw ...;
new Product(name, ...);

// after: 생성자/팩토리 메서드에서 불변식 보장
Product.create(name, ...); // 내부에서 검증, 위반 시 예외
```

단위 테스트로 Entity 레벨에서 불변식 검증 가능해짐을 확인한다.

---

## AI 도구 활용 기록

| 프롬프트 | 시도한 내용 | 관련 문서 |
|----------|-------------|-----------|
| 구현 요구사항을 분석해서 README에 체크리스트와 구현 전략을 작성해줘 | 프로젝트 코드 전체를 분석하여 6단계 체크리스트와 단계별 구현 전략 작성 | [세션 문서](docs/ai-sessions/2026-05-07.md) |
| 계획서 기반 Testcontainers MySQL 통합 테스트 환경 구축 | build.gradle.kts 정리, AbstractIntegrationTest·FlywayMigrationTest 작성, 테스트 Green 확인 | [세션 문서](docs/ai-sessions/2026-05-08.md) |

---

## 현재 상태 요약

| 항목 | 현재 상태 |
|------|-----------|
| 테스트 | 없음 (`.gitkeep`만 존재) |
| Service 계층 | 없음 (Controller가 Repository 직접 사용) |
| 트랜잭션 | 미적용 (`@Transactional` 없음) |
| 위시리스트 자동 삭제 | 주석만 있고 미구현 |
| 도메인 검증 | 외부 유틸 클래스에 위치 |