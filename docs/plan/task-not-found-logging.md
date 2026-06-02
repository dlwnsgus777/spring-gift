# spec: Not Found 로깅 및 orElseThrow 메시지 통일 리팩터링

## 1. 기능 개요

`GlobalExceptionHandler.handleNotFound()`가 예외를 조용히 삼키고 있어 운영 중 404 원인 추적이 불가능하다. 또한 `orElseThrow()` 호출 9곳에 메시지가 없어 로그에 컨텍스트가 남지 않는다. 이 리팩터링은 클라이언트 응답(HTTP 상태 코드, 바디)을 전혀 바꾸지 않으면서 서버 로그에 도메인 컨텍스트를 추가한다.

### 작업 구성

| 섹션 / 기능 | 설명 |
|------------|------|
| **orElseThrow 메시지 통일** | 메시지 없는 9곳에 기존 포맷과 동일한 메시지 추가 — 구조 변경 |
| **GlobalExceptionHandler 로그 추가** | `handleNotFound()`에 `log.warn` 추가 — 서버 동작 변경 |

---

## 2. 비즈니스 로직

> API 설계 변경 없음 — 클라이언트 응답 스펙은 이 리팩터링 전후로 완전히 동일하다.

### 2-1. orElseThrow 메시지 추가 (구조 변경)

메시지 포맷은 기존 코드(`"카테고리가 존재하지 않습니다. id=" + id`)와 동일한 패턴을 따른다. 메시지가 없는 9곳 전부에 추가한다.

| 파일 | 라인 | 추가할 메시지 |
|------|------|-------------|
| `OrderCommandService.java` | 40 | `"옵션이 존재하지 않습니다. id=" + optionId` |
| `OrderCommandService.java` | 46 | `"회원이 존재하지 않습니다. id=" + memberId` |
| `CategoryCommandService.java` | 21 | `"카테고리가 존재하지 않습니다. id=" + id` |
| `WishCommandService.java` | 29 | `"위시리스트가 존재하지 않습니다. id=" + wishId` |
| `MemberQueryService.java` | 24 | `"회원이 존재하지 않습니다. id=" + id` |
| `MemberQueryService.java` | 28 | `"회원이 존재하지 않습니다. email=" + email` |
| `MemberCommandService.java` | 24 | `"회원이 존재하지 않습니다. id=" + id` |
| `MemberCommandService.java` | 34 | `"회원이 존재하지 않습니다. id=" + id` |
| `MemberCommandService.java` | 44 | `"회원이 존재하지 않습니다. id=" + id` |

이미 메시지가 있는 3곳(`CategoryQueryService:21`, `ProductQueryService:23`, `Product:92`)은 변경하지 않는다.

### 2-2. GlobalExceptionHandler 로그 추가 (서버 동작 변경)

- `LoggerFactory.getLogger(GlobalExceptionHandler.class)`로 Logger 선언
- `handleNotFound()`에 `log.warn("Resource not found: {}", e.getMessage())` 한 줄 추가
- 응답 반환 코드(`ResponseEntity.notFound().build()`)는 변경 없음
- 로그 레벨: `warn` — 404는 서버 오류가 아닌 클라이언트 요청 실패이므로 `error`는 부적절

---

## 3. 구현 대상 파일

### common 모듈

| 파일 | 변경 내용 |
|------|---------|
| `GlobalExceptionHandler.java` | Logger 선언 + `log.warn` 추가 |

### 도메인 모듈

| 파일 | 변경 내용 |
|------|---------|
| `OrderCommandService.java` | `orElseThrow()` 2곳에 메시지 추가 |
| `CategoryCommandService.java` | `orElseThrow()` 1곳에 메시지 추가 |
| `WishCommandService.java` | `orElseThrow()` 1곳에 메시지 추가 |
| `MemberQueryService.java` | `orElseThrow()` 2곳에 메시지 추가 |
| `MemberCommandService.java` | `orElseThrow()` 3곳에 메시지 추가 |

### 패키지 위치

```
src/main/java/gift/
├── common/
│   └── GlobalExceptionHandler.java    (수정 — log.warn 추가)
├── order/
│   └── OrderCommandService.java       (수정 — 메시지 2곳)
├── category/
│   └── CategoryCommandService.java    (수정 — 메시지 1곳)
├── wish/
│   └── WishCommandService.java        (수정 — 메시지 1곳)
└── member/
    ├── MemberQueryService.java        (수정 — 메시지 2곳)
    └── MemberCommandService.java      (수정 — 메시지 3곳)
```

### 코드 스니핏

**GlobalExceptionHandler.java**
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Void> handleNotFound(NoSuchElementException e) {
        log.warn("Resource not found: {}", e.getMessage());
        return ResponseEntity.notFound().build();  // 응답 변경 없음
    }

    // 나머지 핸들러 변경 없음
}
```

**orElseThrow 변경 패턴**
```java
// Before
categoryRepository.findById(id).orElseThrow();

// After
categoryRepository.findById(id)
    .orElseThrow(() -> new NoSuchElementException("카테고리가 존재하지 않습니다. id=" + id));
```

---

## 4. 주요 고려사항

1. **응답 불변**: `handleNotFound`의 반환 타입(`ResponseEntity<Void>`)과 상태 코드(404)는 변경하지 않는다. 바디를 추가하면 기존 클라이언트 동작이 달라질 수 있다.
2. **email 개인정보**: `MemberQueryService.findByEmail` 실패 로그에 email이 포함된다. 로그 접근 권한이 적절히 관리되어야 한다.
3. **구조 변경과 동작 변경을 같은 커밋에 섞지 않는다**: orElseThrow 메시지 추가(구조)와 log.warn 추가(동작)는 별도 커밋으로 분리한다.

---

## 5. 구현 순서 (TDD)

> 리팩터링이므로 기존 테스트 Green 유지가 안전망이다. 구조 변경 → 동작 변경 순서를 지킨다.

**[구조 변경] orElseThrow 메시지 통일**

1. [ ] 변경 전 전체 테스트 Green 확인 — `./gradlew test`
2. [ ] `OrderCommandService` — Option, Member `orElseThrow` 메시지 추가
3. [ ] `CategoryCommandService` — Category `orElseThrow` 메시지 추가
4. [ ] `WishCommandService` — Wish `orElseThrow` 메시지 추가
5. [ ] `MemberQueryService` — id, email `orElseThrow` 메시지 추가
6. [ ] `MemberCommandService` — Member `orElseThrow` 3곳 메시지 추가
7. [ ] 변경 후 전체 테스트 Green 확인 — `./gradlew test`

**[동작 변경] GlobalExceptionHandler 로그 추가**

8. [ ] `GlobalExceptionHandler` — Logger 선언 + `log.warn` 추가
9. [ ] 변경 후 전체 테스트 Green 확인 — `./gradlew test`

---

## 6. 인수 조건 (Acceptance Criteria)

- [ ] 404 응답의 HTTP 상태 코드와 바디가 변경 전과 동일하다
- [ ] 존재하지 않는 리소스 조회 시 `WARN` 레벨 로그가 남는다
- [ ] 로그 메시지에 도메인 + 식별자(id 또는 email)가 포함된다
- [ ] 메시지 없는 `orElseThrow()` 9곳 전부에 메시지가 추가된다
- [ ] 구조 변경(orElseThrow 메시지)과 동작 변경(log.warn)이 별도 커밋으로 분리된다
- [ ] 전체 테스트가 Green이다
