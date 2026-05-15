---
name: domain-analyzer
description: Spring Boot 프로젝트의 특정 도메인을 분석해 docs/domain/ 하위에 마크다운 문서를 생성한다. "도메인 분석해줘", "도메인 규칙 추출해줘", "도메인 문서 작성해줘", "<도메인명> 분석해줘", "docs/domain 에 문서 만들어줘", "도메인 정리해줘", "<도메인명> 정리해줘" 등을 요청할 때 사용한다. 도메인 패키지(Entity, Controller, Repository, DTO)와 Flyway 마이그레이션 파일을 읽어 비즈니스 로직을 도메인 관점에서 서술한다.
---

## 목적

도메인 패키지 코드를 읽고, **현재 코드 상태**를 비즈니스 관점에서 해석해 `docs/domain/<domain>_domain.md` 문서를 만든다.

좋은 도메인 분석 문서는 코드 구현 방식이 아니라 **도메인이 시스템에서 어떤 역할을 하고 어떤 규칙을 따르는지**를 담는다.

## 절차

### 1. 소스 파일 수집

도메인 이름이 주어지면 다음 파일을 모두 읽는다:

```
src/main/java/gift/<domain>/          — 도메인 패키지 하위 모든 .java 파일
src/main/resources/db/migration/      — V*.sql 파일에서 해당 도메인 테이블 DDL과 기본 데이터
src/test/java/gift/                   — <domain> 관련 테스트 파일 (있을 경우)
```

### 2. 비즈니스 로직 추출

코드를 읽으면서 다음 질문에 답한다:

- 이 도메인은 시스템에서 무엇을 담당하는가?
- 오퍼레이션별(생성/수정/삭제/조회 등)로 어떤 조건에서 성공/실패하는가?
- 어떤 불변식(invariant)이 있는가? (유니크 제약, NOT NULL, FK 제약 등)
- 다른 도메인에 어떤 영향을 미치는가?
- 인증이 필요한가?

### 3. 문서 생성

템플릿 파일 `.claude/skills/domain-analyzer/assets/domain-template.md`를 읽어 사용한다.
각 플레이스홀더를 분석한 내용으로 치환한다:

| 플레이스홀더 | 치환 내용 |
|-------------|----------|
| `{{DOMAIN_TITLE}}` | 도메인 한글 이름 (예: Category → 카테고리) |
| `{{DATE}}` | 오늘 날짜 (YYYY-MM-DD) |
| `{{BRANCH}}` | 현재 git 브랜치 (`git branch --show-current`) |
| `{{FILE_TREE}}` | 도메인 패키지 파일 트리 (각 파일 역할 한 줄) |
| `{{TEST_FILES}}` | 테스트 파일 목록, 없으면 `테스트 파일: 없음` |
| `{{DOMAIN_ROLE_SUMMARY}}` | 도메인 역할 한 문장 요약 |
| `{{OPERATIONS}}` | 오퍼레이션별 비즈니스 규칙 (소제목 + 산문) |
| `{{SCHEMA_FILE}}` | DDL이 있는 마이그레이션 파일명 (예: V1__Initialize_project_tables.sql) |
| `{{DDL}}` | 해당 도메인 테이블 CREATE 문 |
| `{{DEFAULT_DATA_SECTION}}` | 기본 데이터가 있으면 `### 기본 데이터 (파일명)` + 테이블, 없으면 생략 |
| `{{ENTITY_CODE}}` | Entity 클래스 코드 (필드 + 메서드 시그니처) |
| `{{PUBLIC_API_ROWS}}` | 공개 메서드 표 행 (`\| 메서드 \| 설명 \|` 형식) |
| `{{API_ROWS}}` | API 엔드포인트 표 행 |
| `{{AUTH_DESCRIPTION}}` | 인증 여부 한 문장 (인증 없으면 이 줄 생략 가능) |
| `{{DTO_SECTIONS}}` | Request / Response 각각 코드 블록 + 설명 |
| `{{RELATION_DIAGRAM}}` | 연관 관계 텍스트 다이어그램 |
| `{{RELATION_DESCRIPTION}}` | FK 제약, 참조 방향 설명 불릿 목록 |

저장 경로: `docs/domain/<domain>_domain.md`  
`docs/domain/` 디렉토리가 없으면 생성한다.

## 각 섹션 작성 지침

### 비즈니스 로직 섹션

가장 중요한 섹션이다. 코드를 보지 않은 사람도 이 도메인이 어떻게 동작하는지 이해할 수 있어야 한다.

- 도메인 전체 역할을 한 문장으로 시작한다
- 각 오퍼레이션을 소제목으로 나눠 규칙을 서술한다
- DB 제약이 내포한 비즈니스 규칙도 자연어로 풀어 설명한다 (예: FK NOT NULL → "모든 상품은 반드시 하나의 카테고리에 속해야 한다")
- `ON DELETE CASCADE` 없음처럼 코드에 명시적 언급이 없는 설계 의도도 추론해 서술한다

### API 명세 섹션

인증 여부는 `@AuthenticationPrincipal` 또는 `@RequestHeader("Authorization")` 사용 여부로 판단한다.

## 작성 원칙

- **현재 상태만 서술한다**: 미래 계획, 개선 방향, 문제점, TODO는 포함하지 않는다
- **비즈니스 의미 우선**: "repository.findById를 호출한다"가 아니라 "존재하지 않는 id로 요청하면 404를 반환한다"처럼 서술한다
- **코드 스니펫보다 자연어**: 비즈니스 로직 섹션에서는 코드 대신 문장으로 설명한다
- **완결된 사실만**: 불확실한 추측은 "~로 보인다" 대신 확인 가능한 코드 근거로 대체하거나 생략한다
