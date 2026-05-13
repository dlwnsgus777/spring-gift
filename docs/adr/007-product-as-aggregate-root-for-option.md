# ADR 007: Product를 Option의 애그리게이트 루트로 채택

- **상태**: 수락됨 (Accepted)
- **날짜**: 2026-05-13

---

## 맥락 (Context)

Option 도메인을 리팩터링하면서 `OptionCommandService`를 별도로 추출했으나, 구조적 문제가 드러났다.

**코드 기반 근거:**

1. **`OptionCommandService`가 항상 `ProductQueryService`에 의존** — `create()`와 `delete()` 모두 첫 줄에서 `productQueryService.findById(productId)`를 호출했다. 옵션 단독으로는 어떤 쓰기 작업도 불가능한 구조였다.

2. **`Option.validateDeletion(int totalOptionsCount)`의 어색함** — "최소 1개 옵션" 규칙을 `Option` 엔티티에 두려 했으나, Option은 형제 옵션의 수를 스스로 알 수 없어 서비스에서 count를 주입받아야 했다. 도메인 규칙이 엔티티 안에 있지만 정작 판단에 필요한 데이터를 외부에서 받는 역설적 구조였다.

3. **중복 이름 검증이 DB 쿼리에 의존** — `optionRepository.existsByProductIdAndName(productId, name)`으로 매번 DB를 조회했다. 이미 같은 상품의 옵션을 로드하면 메모리에서 체크할 수 있음에도 별도 쿼리를 발행했다.

4. **`Product`에 이미 애그리게이트 루트 설계가 있었음** — `Product.options`는 `@OneToMany(cascade = ALL, orphanRemoval = true)`로 선언되어 있었다. 이는 JPA 레벨에서 이미 Product가 Option의 생명주기를 소유한다는 의도였으나, 서비스 계층에서는 이를 무시하고 `OptionRepository`를 직접 사용했다.

---

## 결정 (Decision)

`Product`를 Option의 **애그리게이트 루트**로 명시적으로 채택한다.

- `Product.addOption(String name, int quantity)` — 중복 이름 검증을 `options` 컬렉션에서 직접 수행 후 추가
- `Product.removeOption(Long optionId)` — "최소 1개" 불변식을 `options.size()`로 직접 판단 후 컬렉션에서 제거 (`orphanRemoval`이 DELETE를 자동 처리)
- `ProductCommandService.addOption()` / `removeOption()` — `productQueryService.findById()`로 Product를 로드하고 위 메서드에 위임
- `OptionController`의 쓰기 작업은 `ProductCommandService`를 통해 수행
- `OptionCommandService`는 삭제. `OptionQueryService`는 읽기 전용 조회를 유지

```java
// Product.java
public Option addOption(String name, int quantity) {
    boolean duplicate = options.stream().anyMatch(o -> o.getName().equals(name));
    if (duplicate) throw new IllegalArgumentException("이미 존재하는 옵션명입니다.");
    Option option = new Option(this, name, quantity);
    options.add(option);
    return option;
}

public void removeOption(Long optionId) {
    if (options.size() <= 1) throw new IllegalArgumentException("옵션이 1개인 상품은 옵션을 삭제할 수 없습니다.");
    Option option = options.stream()
        .filter(o -> Objects.equals(o.getId(), optionId))
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException("옵션이 존재하지 않습니다. id=" + optionId));
    options.remove(option);
}
```

---

## 고려한 대안 (Alternatives Considered)

| 대안 | 기각 이유 |
|------|----------|
| `OptionCommandService` 유지 (기존 방식) | "최소 1개" 검증이 `Option.validateDeletion(count)`처럼 count를 외부 주입으로 받아야 하는 구조적 어색함이 해소되지 않음. 서비스가 항상 ProductQueryService에 의존하는 것 자체가 잘못된 경계 설정의 신호 |
| `Option`에 불변식 검증 위임 유지 | Option은 자신의 형제 수를 알 수 없으므로, 이 규칙은 컬렉션을 소유한 Product의 책임이 맞음 |

---

## 결과 (Consequences)

**긍정적:**
- "최소 1개 옵션" 불변식이 `Product.removeOption()` 내부에 위치 — 컬렉션을 소유한 객체가 불변식을 직접 보장함
- 중복 이름 검증이 `options` 컬렉션 메모리 체크로 대체 — 별도 DB 조회(`existsByProductIdAndName`) 제거
- `OptionCommandService` 제거로 클래스 수 감소, `OptionController` 의존 그래프 단순화
- `orphanRemoval = true` 설계 의도가 실제 코드에서도 일관되게 반영됨

**부정적:**
- `Product` 엔티티의 책임 범위 증가 — 상품 정보 관리 외에 옵션 컬렉션 불변식 보장 역할 추가
- 옵션 조작 시 `Product`를 반드시 로드해야 함 — 옵션만 단독으로 다루는 경로가 없음 (현재 규모에서는 문제없으나 옵션 수가 매우 많아지면 컬렉션 로드 비용 고려 필요)
- `ProductCommandServiceTest`에서 옵션 사전 데이터 설정 시 반드시 `productCommandService.addOption()`을 경유해야 함 — `optionRepository.save()` 직접 사용 시 Hibernate L1 캐시 불일치 발생
