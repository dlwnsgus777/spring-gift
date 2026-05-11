# spec: 4단계 Point 도메인 — PointCommandService 추출 + Point VO 생성

## 1. 기능 개요

두 가지 작업을 이번 단계에서 함께 완료한다.

1. **서비스 추출**: `MemberCommandService`에 혼재된 포인트 충전 로직을 `gift/point/PointCommandService`로 분리한다.
2. **도메인 모델 개선**: `Member`의 `int point` 필드를 JPA `@Embeddable` VO인 `Point`로 교체한다. 포인트 불변식(amount > 0, 잔액 충분)이 `Point` 타입 내부에 캡슐화되며, DB 스키마는 변경되지 않는다.

### 기능 구성

| 대상 | 유형 | 변경 내용 |
|------|------|----------|
| `gift/point/Point.java` | 신규 | `@Embeddable` VO — 충전·차감 불변식 캡슐화 |
| `gift/point/PointTest.java` | 신규 | Point VO 단위 테스트 |
| `gift/point/PointCommandService.java` | 신규 | 포인트 충전 서비스 |
| `gift/point/PointCommandServiceTest.java` | 신규 | chargePoint 성공·실패 통합 테스트 |
| `gift/member/Member.java` | 수정 | `int point` → `@Embedded Point point` |
| `gift/member/MemberCommandService.java` | 수정 | `chargePoint()` 삭제 |
| `gift/member/AdminMemberController.java` | 수정 | `PointCommandService` 주입으로 교체 |
| `gift/member/MemberCommandServiceTest.java` | 수정 | `test06`·`test07` 삭제 |

---

## 2. API 설계

API 변경 없음. 엔드포인트·요청·응답 스펙은 그대로 유지된다.

```
POST /admin/members/{id}/charge-point
Content-Type: application/x-www-form-urlencoded

amount=1000
```

**성공 응답:** 302 Redirect → `/admin/members`

---

## 3. 비즈니스 로직

### 3-1. Point VO 불변식

`Point`는 불변 객체(immutable value object)다. `charge()`와 `deduct()`는 새 `Point` 인스턴스를 반환한다.

- `charge(int amount)`: `amount ≤ 0`이면 `IllegalArgumentException`
- `deduct(int amount)`: `amount ≤ 0`이면 `IllegalArgumentException`; `amount > value`이면 `IllegalArgumentException("포인트가 부족합니다.")`
- 초기값: `value = 0` (DB `DEFAULT 0` 반영)

### 3-2. Member와 Point 관계

`Member`는 `@Embedded Point point`를 가진다. `chargePoint()`·`deductPoint()`는 `Point` VO에 위임한다.

```
member.chargePoint(amount)
  → this.point = this.point.charge(amount)  // 새 Point 인스턴스로 교체
```

JPA dirty checking이 `Member.point` 변경을 감지해 자동으로 UPDATE한다. Flyway 마이그레이션 불필요 — `@Column(name = "point")`으로 동일 컬럼 매핑.

### 3-3. 포인트 충전 흐름

1. `AdminMemberController`가 `PointCommandService.charge(id, amount)`를 호출한다.
2. `PointCommandService`가 `MemberQueryService.findById(id)`로 회원을 조회한다. 없으면 `NoSuchElementException` → 404.
3. `member.chargePoint(amount)`를 호출한다. `amount ≤ 0`이면 `IllegalArgumentException` → 400.
4. `@Transactional` dirty checking으로 포인트 변경이 자동 저장된다. 별도 `save()` 불필요.

### 3-4. 트랜잭션 전파

`PointCommandService`는 `@Transactional`(읽기-쓰기), `MemberQueryService`는 `@Transactional(readOnly=true)`.
내부에서 `MemberQueryService.findById()`를 호출하면 `PROPAGATION_REQUIRED`에 의해 외부 트랜잭션에 합류한다.
반환된 `Member` 엔티티는 읽기-쓰기 트랜잭션 컨텍스트에서 관리되므로 dirty checking이 정상 동작한다.

---

## 4. 구현 대상 파일

### 패키지 위치

```
src/main/java/gift/
├── member/
│   ├── Member.java              (수정 — @Embedded Point point)
│   ├── AdminMemberController.java (수정 — PointCommandService 주입)
│   ├── MemberCommandService.java  (수정 — chargePoint() 삭제)
│   └── ...
└── point/                       (신규 패키지)
    ├── Point.java               (신규 — @Embeddable VO)
    └── PointCommandService.java (신규)

src/test/java/gift/
├── member/
│   └── MemberCommandServiceTest.java (수정 — test06·test07 삭제)
└── point/                            (신규 패키지)
    ├── PointTest.java                (신규 — 순수 단위 테스트)
    └── PointCommandServiceTest.java  (신규 — 통합 테스트)
```

### 코드 스니핏

**`Point.java` (신규 — `gift/point` 패키지)**

```java
package gift.point;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class Point {

    @Column(name = "point")
    private int value;

    protected Point() {
        this.value = 0;
    }

    public Point(int value) {
        this.value = value;
    }

    public Point charge(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        return new Point(this.value + amount);
    }

    public Point deduct(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("차감 금액은 1 이상이어야 합니다.");
        }
        if (amount > this.value) {
            throw new IllegalArgumentException("포인트가 부족합니다.");
        }
        return new Point(this.value - amount);
    }

    public int getValue() {
        return value;
    }
}
```

**`Member.java` 변경 부분**

```java
// before
private int point;

public void chargePoint(int amount) {
    if (amount <= 0) throw new IllegalArgumentException("Amount must be greater than zero.");
    this.point += amount;
}

public void deductPoint(int amount) {
    if (amount <= 0) throw new IllegalArgumentException("차감 금액은 1 이상이어야 합니다.");
    if (amount > this.point) throw new IllegalArgumentException("포인트가 부족합니다.");
    this.point -= amount;
}

public int getPoint() { return point; }

// after
@Embedded
private Point point = new Point(0);

public void chargePoint(int amount) {
    this.point = this.point.charge(amount);
}

public void deductPoint(int amount) {
    this.point = this.point.deduct(amount);
}

public int getPoint() { return point.getValue(); }
```

**`PointCommandService.java` (신규)**

```java
package gift.point;

import gift.member.Member;
import gift.member.MemberQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PointCommandService {

    private final MemberQueryService memberQueryService;

    public PointCommandService(MemberQueryService memberQueryService) {
        this.memberQueryService = memberQueryService;
    }

    public Member charge(Long memberId, int amount) {
        Member member = memberQueryService.findById(memberId);
        member.chargePoint(amount);
        return member;
    }
}
```

**`AdminMemberController.java` 변경 부분**

```java
// before — MemberCommandService만 주입
private final MemberCommandService memberCommandService;

@PostMapping("/{id}/charge-point")
public String chargePoint(@PathVariable Long id, @RequestParam int amount) {
    memberCommandService.chargePoint(id, amount);
    return "redirect:/admin/members";
}

// after — PointCommandService 추가 주입
private final MemberQueryService memberQueryService;
private final MemberCommandService memberCommandService;
private final PointCommandService pointCommandService;

@PostMapping("/{id}/charge-point")
public String chargePoint(@PathVariable Long id, @RequestParam int amount) {
    pointCommandService.charge(id, amount);
    return "redirect:/admin/members";
}
```

**`PointTest.java` (신규 — 순수 단위 테스트, Spring 컨텍스트 불필요)**

```java
package gift.point;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointTest {

    @Test
    @DisplayName("양수 금액을 충전하면 잔액이 증가한다")
    void test01() {
        // arrange
        Point point = new Point(1000);

        // act
        Point result = point.charge(500);

        // assert
        assertThat(result.getValue()).isEqualTo(1500);
    }

    @Test
    @DisplayName("0 이하 금액을 충전하면 IllegalArgumentException을 던진다")
    void test02() {
        // arrange
        Point point = new Point(1000);

        // act & assert
        assertThatThrownBy(() -> point.charge(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("잔액 이하 금액을 차감하면 잔액이 감소한다")
    void test03() {
        // arrange
        Point point = new Point(1000);

        // act
        Point result = point.deduct(300);

        // assert
        assertThat(result.getValue()).isEqualTo(700);
    }

    @Test
    @DisplayName("잔액보다 큰 금액을 차감하면 IllegalArgumentException을 던진다")
    void test04() {
        // arrange
        Point point = new Point(100);

        // act & assert
        assertThatThrownBy(() -> point.deduct(200))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("포인트가 부족합니다.");
    }

    @Test
    @DisplayName("0 이하 금액을 차감하면 IllegalArgumentException을 던진다")
    void test05() {
        // arrange
        Point point = new Point(1000);

        // act & assert
        assertThatThrownBy(() -> point.deduct(0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

**`PointCommandServiceTest.java` (신규)**

```java
package gift.point;

import gift.AbstractIntegrationTest;
import gift.member.Member;
import gift.member.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class PointCommandServiceTest extends AbstractIntegrationTest {

    @Autowired
    private PointCommandService pointCommandService;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("포인트를 충전하면 회원 잔액이 증가한다")
    void test01() {
        // arrange
        Member saved = memberRepository.save(new Member("point@example.com", "pass"));
        int before = saved.getPoint();

        // act
        Member result = pointCommandService.charge(saved.getId(), 500);

        // assert
        assertThat(result.getPoint()).isEqualTo(before + 500);
    }

    @Test
    @DisplayName("존재하지 않는 회원 ID로 충전하면 NoSuchElementException을 던진다")
    void test02() {
        // act & assert
        assertThatThrownBy(() -> pointCommandService.charge(999999L, 100))
            .isInstanceOf(NoSuchElementException.class);
    }
}
```

---

## 5. 주요 고려사항

1. **DB 컬럼 매핑**: `Point.value`에 `@Column(name = "point")`를 명시해야 Flyway 마이그레이션 없이 기존 컬럼에 매핑된다.

2. **JPA `protected` 기본 생성자**: `@Embeddable` 클래스는 JPA가 리플렉션으로 인스턴스를 생성하므로 `protected Point()` 기본 생성자가 필요하다.

3. **`AdminMemberController` 생성자 시그니처**: `chargePoint()` 처리를 `PointCommandService`로 옮기지만, 다른 회원 CRUD 메서드(`create`, `update`, `delete`)는 여전히 `MemberCommandService`가 필요하다. 두 서비스를 모두 주입받아야 한다.

4. **`OrderController`의 `member.deductPoint()`**: 이번 단계에서 변경하지 않는다. `Member.deductPoint()`가 내부적으로 `point.deduct()`에 위임하므로 외부 동작은 동일하게 유지된다.

---

## 6. 구현 순서 (TDD)

**[도메인 모델 개선 — Point VO]**

1. [x] `PointTest` 작성 — test01~test05 (Red: 클래스 미존재로 컴파일 에러)
2. [x] `gift/point/Point.java` 구현 (Green)
3. [x] `Member` 수정 — `@Embedded Point point`, `chargePoint()`·`deductPoint()` 위임으로 변경
4. [x] 기존 전체 테스트 Green 확인 (동작 보존 검증)

**[서비스 추출 시도 → 철회 — ADR 006 참고]**

5. [x] `PointCommandServiceTest` test01 작성 후 `PointCommandService` 구현
6. [x] `PointCommandService` 검토 결과 제거 결정 — Point 서비스가 Member 서비스에 의존하고 Member를 반환하는 구조가 부자연스러움
7. [x] `PointCommandService`·`PointCommandServiceTest` 삭제, `MemberCommandService.chargePoint()` 복원

**[최종 검증]**

11. [x] 전체 테스트 Green 확인

---

## 7. 인수 조건 (Acceptance Criteria)

- [x] `gift/point/Point.java`가 `@Embeddable`로 존재하며 `charge()`·`deduct()` 불변식을 캡슐화한다
- [x] `PointTest` test01~test05가 모두 Green이다 (Spring 컨텍스트 없는 순수 단위 테스트)
- [x] `Member`가 `@Embedded Point point`를 사용하고, `int point` 필드가 존재하지 않는다
- [x] `MemberCommandService.chargePoint()`가 존재하며 `Point.charge()`에 위임한다 (PointCommandService 불필요 — ADR 006)
- [x] `AdminMemberController.POST /{id}/charge-point` 통합 테스트(`AdminMemberControllerTest.test06`)가 Green이다
- [x] 전체 테스트가 Green이다
