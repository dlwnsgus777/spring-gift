# spec: 테스트 환경 구축 (1단계)

## 1. 기능 개요

현재 프로젝트에는 테스트가 전혀 없고, 개발 환경은 H2(인메모리)인 반면 프로덕션은 MySQL을 사용한다.
H2와 MySQL은 SQL 방언 차이가 있어 H2 테스트는 거짓 안전망을 제공한다.
Testcontainers로 MySQL 컨테이너를 띄워 프로덕션과 동일한 환경에서 Flyway 마이그레이션이 자동 적용됨을 검증한다.

### 기능 구성

| 작업 항목 | 설명 |
|----------|------|
| **build.gradle.kts 수정** | Testcontainers MySQL 모듈 의존성 추가 (BOM 미사용 — Spring Boot 의존성 관리로 버전 자동 관리) |
| **AbstractIntegrationTest** | MySQL 컨테이너 기반 공통 베이스 클래스 작성 |
| **Flyway 마이그레이션 검증 테스트** | 컨테이너 기동 후 V1, V2 마이그레이션 정상 적용 확인 |

---

## 2. API 설계

_해당 없음 — 테스트 인프라 구축 작업으로 API 변경 없음_

---

## 3. 비즈니스 로직

### 3-1. Testcontainers 컨테이너 생명주기

- `MySQLContainer`를 `static` 필드로 선언해 **JVM 당 1개** 컨테이너만 기동
- `@Container` + `static` 조합으로 테스트 클래스 간 컨테이너 재사용 → 전체 테스트 시간 단축
- Spring의 `@DynamicPropertySource`로 DataSource URL/username/password를 컨테이너 값으로 오버라이드

### 3-2. Flyway 마이그레이션 자동 적용 흐름

```
컨테이너 기동 (MySQL 8.0)
  → Spring Context 로딩
  → Flyway가 V1__Initialize_project_tables.sql 실행
  → Flyway가 V2__Insert_default_data.sql 실행
  → 테스트: flyway_schema_history 테이블에 2개 레코드 확인
```

---

## 4. 구현 대상 파일

### 테스트 인프라 모듈

| 파일 | 역할 |
|------|------|
| `build.gradle.kts` | `testcontainers-mysql`, `junit-jupiter` 의존성 추가 (BOM 미사용) |
| `AbstractIntegrationTest` | `@SpringBootTest` + `@Testcontainers` + MySQL 컨테이너 설정 |
| `FlywayMigrationTest` | Flyway V1/V2 마이그레이션 정상 적용 검증 |

### 패키지 위치

```
src/test/java/gift/
├── AbstractIntegrationTest.java     (신규 — 공통 베이스 클래스)
└── FlywayMigrationTest.java         (신규 — Flyway 적용 확인 테스트)
```

### 코드 스니핏

#### `build.gradle.kts` 추가 부분

```kotlin
// 기존 dependencies 블록 내에 추가
dependencies {
    // 기존 의존성들 유지 ...

    // BOM 미사용 — io.spring.dependency-management 플러그인이 버전 자동 관리
    testImplementation("org.testcontainers:mysql")
    testImplementation("org.testcontainers:junit-jupiter")
}
```

> **결정**: `platform("org.testcontainers:testcontainers-bom:...")` 불필요. Spring Boot 의존성 관리가 Testcontainers 버전을 이미 관리한다.
> `mysql-connector-j`는 `runtimeOnly`에 이미 포함되어 있어 `testRuntimeOnly` 추가 불필요.

---

#### `AbstractIntegrationTest.java`

```java
package gift;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("gift_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }
}
```

---

#### `FlywayMigrationTest.java`

```java
package gift;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationTest extends AbstractIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Test
    void flyway_마이그레이션이_정상_적용된다() {
        // arrange
        // (AbstractIntegrationTest가 컨테이너와 Spring Context를 준비)

        // act
        MigrationInfo[] appliedMigrations = flyway.info().applied();

        // assert
        assertThat(appliedMigrations).hasSize(2);
        assertThat(appliedMigrations[0].getDescription()).contains("Initialize");
        assertThat(appliedMigrations[1].getDescription()).contains("Insert");
    }
}
```

---

---

## 5. 주요 고려사항

1. ~~**MySQL Connector/J 의존성 중복**~~ → **해결됨**: `runtimeOnly("com.mysql:mysql-connector-j")` 이미 존재. `testRuntimeOnly` 추가 불필요.

2. **application.properties의 H2 설정 충돌**: 현재 `spring.datasource.*` 설정이 H2로 되어 있다면 `@DynamicPropertySource`가 런타임에 오버라이드하므로 충돌 없음. 단, `spring.jpa.hibernate.ddl-auto=create-drop` 등이 있으면 Flyway와 충돌 가능.
   - 대안: `src/test/resources/application-test.properties`에 `spring.jpa.hibernate.ddl-auto=validate` 설정

3. ~~**Testcontainers 버전 호환성**~~ → **해결됨**: BOM 미사용, Spring Boot 의존성 관리로 자동 해결.

4. ~~**commons-compress CVE 오버라이드**~~ → **해결됨**: Spring Boot 3.5.9 의존성 관리가 안전한 버전을 이미 관리. `configurations.all` 블록 불필요하여 제거.

---

## 6. 구현 순서 (TDD)

1. [x] `build.gradle.kts` — Testcontainers mysql 모듈 + junit-jupiter 의존성 추가 (BOM 미사용 — Spring Boot 관리)
2. [x] `build.gradle.kts` — MySQL Connector/J 의존성 확인 (이미 runtimeOnly에 포함 — 추가 불필요)
   - `configurations.all` commons-compress CVE 오버라이드 불필요 확인 → 제거
3. [x] `AbstractIntegrationTest.java` — MySQL 컨테이너 + `@DynamicPropertySource` 작성
4. [x] `FlywayMigrationTest.java` — Flyway 마이그레이션 적용 검증 테스트 작성
5. [x] 로컬에서 테스트 실행해 Green 확인 (`./gradlew test`)

---

## 7. 인수 조건 (Acceptance Criteria)

- [x] `./gradlew test` 실행 시 MySQL 컨테이너가 자동으로 기동된다
- [x] `FlywayMigrationTest`가 Green — V1, V2 마이그레이션 2개가 정상 적용됨을 검증
- [x] `AbstractIntegrationTest`를 상속하는 모든 테스트 클래스가 동일한 컨테이너 인스턴스를 재사용한다 (컨테이너 1회만 기동)
- [x] H2 의존성을 제거하지 않아도 테스트 환경은 MySQL 컨테이너를 사용한다
