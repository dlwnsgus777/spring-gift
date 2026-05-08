# ADR 001: 통합 테스트 환경으로 Testcontainers 도입

- **상태**: 수락됨 (Accepted)
- **날짜**: 2026-05-08

---

## 맥락 (Context)

현재 프로젝트는 테스트가 전혀 없는 상태(`src/test/`에 `.gitkeep`만 존재)이며,
앞으로 리팩터링(서비스 계층 추출, 트랜잭션 경계 설정 등)을 안전하게 진행하려면 신뢰할 수 있는 테스트 환경이 필요하다.

현재 환경 구성의 구체적 상황:

- `application.properties`에 `spring.datasource` 설정이 없음 → Spring Boot가 H2 인메모리 DB를 자동 구성
- `build.gradle.kts` 32번째 줄: `implementation("org.flywaydb:flyway-mysql")` — 마이그레이션 SQL은 MySQL을 타겟으로 작성됨
- `V1__Initialize_project_tables.sql` 48번째 줄: `order_date_time timestamp not null` — MySQL `timestamp`는 타임존 자동 변환 + 2038년 한계라는 MySQL 고유 동작을 가짐

이 상태에서 H2 기반 테스트를 작성하면, **H2에서 통과한 테스트가 프로덕션 MySQL에서 실패**하는 "거짓 안전망" 문제가 발생한다.

---

## 결정 (Decision)

통합 테스트 환경으로 **Testcontainers(MySQL 컨테이너)** 를 도입한다.

```
Testcontainers(실제 MySQL 컨테이너) + Flyway 자동 적용
→ 테스트 환경 = 프로덕션 환경 (동일한 DB 엔진)
→ Docker만 있으면 누구나 동일한 결과
→ CI에서도 동일하게 실행
```

공통 베이스 클래스 `AbstractIntegrationTest`를 작성해 컨테이너 설정을 한 곳에서 관리한다.

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

## 고려한 대안 (Alternatives Considered)

| 대안 | 기각 이유 |
|------|----------|
| H2 인메모리 그대로 사용 | MySQL과 동작 차이 존재(`timestamp` 처리, FK 동작 등) — 거짓 안전망 |
| 로컬 MySQL 직접 연결 | 개발 환경마다 설정이 다르고, CI에서 재현 보장 불가 |
| Embedded MySQL (Wix) | 유지보수 중단, Java 21 미지원 |

---

## 결과 (Consequences)

**긍정적:**
- 테스트가 실제 MySQL 위에서 실행되므로 통과 = 프로덕션 동작 보장
- Flyway 마이그레이션이 테스트 컨테이너에 자동 적용되어 스키마 검증 가능
- Docker만 있으면 팀 전체, CI 모두 동일한 환경 재현 가능
- 이후 리팩터링(2~6단계)의 회귀 검증 기반이 됨

**부정적:**
- Docker가 없는 환경에서는 테스트 실행 불가
- H2 대비 테스트 시작 시간 증가 (컨테이너 기동 오버헤드)
- `testcontainers` 의존성 추가 필요
