# ADR 006: Point를 @Embeddable VO로 추출하고 PointCommandService를 제거

- **상태**: 수락됨 (Accepted)
- **날짜**: 2026-05-11

---

## 맥락 (Context)

`Member` 엔티티(`gift/member/Member.java`)는 `int point` 필드를 가지고 있었고, 포인트 검증 로직(amount > 0, 잔액 부족 체크)이 `Member.chargePoint()`와 `Member.deductPoint()` 메서드 안에 직접 구현되어 있었다.

4단계 목표는 "포인트 충전 로직을 `gift/point` 패키지로 분리"였다. 초기 구현 계획에 따라 두 가지 작업을 시도했다:

1. `Point @Embeddable` VO 생성 — `Member`의 `int point`를 타입으로 교체
2. `PointCommandService` 신규 생성 — `AdminMemberController`의 충전 흐름을 새 서비스로 이관

`PointCommandService`를 구현하자 다음 문제가 드러났다:
- `PointCommandService`가 `MemberQueryService`를 의존해 Point 서비스가 Member 서비스에 역방향 의존하는 형태가 됨
- `PointCommandService.charge(Long memberId, int amount)`가 `Member`를 반환 — Point 서비스의 반환 타입이 Member
- 실질적으로 `memberQueryService.findById(id)` 후 `member.chargePoint(amount)` 한 줄 호출에 불과한 thin wrapper

포인트 충전은 `Member` 집합체(aggregate)에 대한 연산이며, `point`는 Member가 소유하는 값 객체다. `PointCommandService`는 서비스 계층의 책임 분리가 아니라 억지로 만든 계층이었다.

---

## 결정 (Decision)

**`Point`를 `@Embeddable` VO로 추출하되, `PointCommandService`는 만들지 않는다.**

`Point` 클래스(`gift/point/Point.java`)를 `@Embeddable`로 생성하고 포인트 불변식을 캡슐화한다:

```java
@Embeddable
public class Point {
    @Column(name = "point")
    private int value;

    public Point charge(int amount) { ... }  // amount ≤ 0 → IllegalArgumentException
    public Point deduct(int amount) { ... }  // 잔액 부족 → IllegalArgumentException
}
```

`Member`는 `@Embedded Point point`를 가지며 `chargePoint()`·`deductPoint()`를 Point에 위임한다:

```java
@Embedded
private Point point = new Point(0);

public void chargePoint(int amount) { this.point = this.point.charge(amount); }
public void deductPoint(int amount) { this.point = this.point.deduct(amount); }
```

포인트 충전 흐름(`findById` → `chargePoint`)은 기존처럼 `MemberCommandService.chargePoint()`가 담당한다. `AdminMemberController`는 변경 없이 `MemberCommandService`를 그대로 사용한다.

---

## 고려한 대안 (Alternatives Considered)

| 대안 | 기각 이유 |
|------|----------|
| `PointCommandService` 유지 (MemberQueryService 의존, Member 반환) | Point 서비스가 Member 서비스에 의존하고 Member를 반환 — 도메인 경계를 역방향으로 침범 |
| `PointCommandService`가 Member를 직접 파라미터로 받는 방식 | `charge(Member member, int amount)`는 결국 `member.chargePoint(amount)` 한 줄짜리 래퍼 — 존재 이유 없음 |
| `int point` 그대로 유지, 서비스 추출만 진행 | 포인트 불변식이 Member 메서드에 분산된 채로 남아 Point 도메인 규칙의 독립 테스트 불가 |

---

## 결과 (Consequences)

**긍정적:**
- 포인트 불변식(amount > 0, 잔액 충분)이 `Point` 타입 내부에 완전히 캡슐화됨
- `PointTest`(test01~test05)가 Spring 컨텍스트 없는 순수 Java 단위 테스트로 작성 가능
- `Member.chargePoint()`·`deductPoint()`가 위임만 수행 — Member가 포인트 검증 규칙을 직접 알지 않아도 됨
- DB 스키마 변경 없음 — `@Column(name = "point")`으로 기존 컬럼 그대로 매핑

**부정적:**
- `gift/point` 패키지에 `Point.java`만 존재 — 패키지 하나에 클래스 하나인 구조
- 포인트 충전 흐름이 `MemberCommandService`에 남아 Member와 Point의 책임 경계가 패키지 수준에서는 드러나지 않음
- `Point`가 단일 `int` 필드를 래핑하는 VO이므로 현재 시점에는 복잡도 대비 이득이 크지 않음 — 포인트가 금액+만료일 같은 복합 필드로 성장할 때 VO의 가치가 비로소 명확해짐
