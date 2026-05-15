# ADR 003: 테스트 유형별 격리 전략 및 Testcontainers 싱글턴 패턴 적용

- **상태**: 수락됨 (Accepted)
- **날짜**: 2026-05-10

---

## 맥락 (Context)

Category 도메인 통합 테스트를 작성하는 과정에서 세 가지 문제가 드러났다.

**문제 1 — Testcontainers 컨테이너 수명**

`AbstractIntegrationTest`에 `@Testcontainers` + `@Container static MySQLContainer`를 선언했을 때, JUnit 5의 Testcontainers 확장이 각 테스트 클래스 종료 시점에 static 컨테이너를 내려버렸다. `CategoryQueryServiceTest`가 끝나면 MySQL이 종료되고, 이어서 실행되는 `CategoryCommandServiceTest`에서 `java.net.ConnectException`이 발생했다.

**문제 2 — MockMvc 테스트의 @Transactional 무효**

MockMvc(`@SpringBootTest` + HTTP 요청)는 서블릿 컨테이너 내 별도 스레드에서 요청을 처리한다. 테스트 메서드에 `@Transactional`을 붙여도 롤백이 적용되지 않아 테스트가 생성한 데이터가 DB에 남는다. 기존에는 `@BeforeEach`에서 `categoryRepository.deleteAll()`로 초기화했는데, FK 제약(product → category)으로 실패했고, `SET FOREIGN_KEY_CHECKS=0` 우회도 다른 테스트와 병렬 실행 시 간섭을 일으켰다.

**문제 3 — 서비스 테스트의 Mockito 의존**

서비스 단위 테스트를 Mockito로 작성하면 Repository를 mock하기 때문에 실제 SQL 쿼리, FK 제약, 트랜잭션 동작을 검증하지 못한다. 이 프로젝트는 부팅 시 Flyway로 시드 데이터를 삽입하므로 실제 DB를 활용하는 것이 더 현실적인 테스트가 된다.

---

## 결정 (Decision)

### 1. Testcontainers 싱글턴 패턴

`@Testcontainers`와 `@Container` 어노테이션을 제거하고, `static` 초기화 블록에서 컨테이너를 직접 시작한다. JVM이 종료될 때 Testcontainers의 Ryuk 컨테이너가 자동으로 정리한다.

```java
// AbstractIntegrationTest.java
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("gift_test")
        .withUsername("test")
        .withPassword("test");

    static {
        mysql.start(); // JVM 종료 시까지 유지
    }

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) { ... }
}
```

### 2. 테스트 유형별 격리 전략

| 테스트 유형 | 격리 방법 | 이유 |
|------------|----------|------|
| `*ControllerTest` (MockMvc) | UUID 포함 이름으로 데이터 생성, `deleteAll` 없음 | MockMvc는 별도 스레드 — `@Transactional` 롤백 불가 |
| `*QueryServiceTest` | `@Transactional` — 자동 롤백 | 서비스 직접 호출은 같은 스레드에서 실행 |
| `*CommandServiceTest` | `@Transactional` — 자동 롤백 | 서비스 직접 호출은 같은 스레드에서 실행 |

ControllerTest에서 시드 데이터 존재 여부를 검증할 때는 `containsExactlyInAnyOrder` 대신 `contains`를 사용해 다른 테스트가 남긴 데이터와 충돌하지 않도록 한다.

### 3. 서비스 테스트에서 Mockito 대신 실제 DB

`*QueryServiceTest`, `*CommandServiceTest`는 `AbstractIntegrationTest`를 상속해 실제 MySQL 컨테이너에서 실행한다. Flyway 시드 데이터(`V2__Insert_default_data.sql`)를 arrange 단계의 기반 데이터로 활용한다.

### 4. Service 레이어 반환 타입

Service는 도메인 객체(`Entity`)를 반환하고, `*Response` DTO 변환은 Controller에서 담당한다. Service가 표현 계층 타입을 알지 않아도 된다.

```java
// CategoryQueryService — 도메인 객체 반환
public List<Category> findAll() { ... }

// CategoryController — Response 변환 담당
public ResponseEntity<List<CategoryResponse>> getCategories() {
    List<CategoryResponse> response = categoryQueryService.findAll().stream()
        .map(CategoryResponse::from)
        .toList();
    return ResponseEntity.ok(response);
}
```

---

## 고려한 대안 (Alternatives Considered)

| 대안 | 기각 이유 |
|------|----------|
| `@Container static` 유지 + `@TestClassOrder`로 실행 순서 고정 | 순서 의존성이 생겨 테스트 추가/삭제 시 매번 순서를 관리해야 함 |
| MockMvc 테스트에 `@Transactional` + `@Rollback` | MockMvc 요청이 별도 스레드에서 처리되므로 롤백이 적용되지 않음 — 실제로 검증했을 때 데이터가 남았음 |
| ControllerTest에서 `@BeforeEach deleteAll()` 유지 | FK 제약(product → category)으로 `deleteAll()` 자체가 실패. `SET FOREIGN_KEY_CHECKS=0` 우회는 병렬 실행 시 다른 테스트와 간섭 |
| 서비스 테스트를 Mockito 단위 테스트로 유지 | 실제 SQL, FK 제약, 트랜잭션 경계를 검증하지 못함. 이 프로젝트의 시드 데이터를 활용하지 못해 현실성이 낮음 |
| Service가 `*Response` DTO를 반환 | Service가 표현 계층(HTTP)에 종속됨. 같은 Service를 다른 표현 계층(WebSocket, CLI 등)에서 재사용할 때 불필요한 변환 강제 |

---

## 결과 (Consequences)

**긍정적:**
- MySQL 컨테이너가 JVM 내에서 한 번만 기동되어 테스트 스위트 전체 실행 속도가 빠름
- `--max-workers=2` 병렬 실행에서도 모든 테스트가 Green
- 서비스 테스트가 실제 DB 동작(FK 제약, 트랜잭션, Flyway 스키마)을 검증
- Service 레이어가 표현 계층 타입에 의존하지 않아 재사용성 향상

**부정적:**
- ControllerTest가 생성한 UUID 기반 데이터가 DB에 누적됨 (CI 환경은 컨테이너 재시작으로 자연 정리되나, 장시간 실행 시 데이터 증가)
- `@Transactional` 롤백 방식은 서비스 테스트에서만 유효하므로, 테스트 유형별 격리 전략이 달라 팀원이 처음 볼 때 혼란스러울 수 있음 (CLAUDE.md에 전략 명시로 보완)
