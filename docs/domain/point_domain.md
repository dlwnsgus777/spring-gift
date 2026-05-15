# 포인트 도메인 분석

> 작성일: 2026-05-11  
> 최종 수정일: 2026-05-15  
> 분석 대상 브랜치: feature/youth-6-fifth-assignment

---

## 변경 히스토리

| 날짜 | 내용 |
|------|------|
| 2026-05-11 | 최초 분석 — 포인트 로직이 Member 엔티티 내에 `int point`로 인라인 위치 |
| 2026-05-15 | `gift/point` 패키지 신설, `Point` `@Embeddable` 불변 값 객체 추출; `OrderCommandService` 분리로 트랜잭션 버그 수정; 위시 자동 삭제 구현 완료 |

---

## 1. 파일 구성

```
src/main/java/gift/point/
└── Point.java               — @Embeddable 불변 값 객체 (충전·차감 로직 캡슐화)

src/main/java/gift/member/
├── Member.java              — @Embedded Point 보유
├── MemberCommandService.java — chargePoint(id, amount) 트랜잭션 처리
├── MemberRepository.java    — 포인트 전용 메서드 없음, JpaRepository 기본 활용
└── AdminMemberController.java — POST /{id}/charge-point 엔드포인트 (Admin UI)

src/main/java/gift/order/
└── OrderCommandService.java  — createOrder() 내에서 포인트 차감 (@Transactional)
```

**테스트 파일:**
- `src/test/java/gift/member/AdminMemberControllerTest.java` — `test06`: POST /{id}/charge-point 통합 테스트
- `src/test/java/gift/member/MemberCommandServiceTest.java` — `test06`, `test07`: chargePoint 성공·실패 단위 테스트

---

## 2. 비즈니스 로직

포인트는 `Member` 엔티티가 보유한 잔액으로, 관리자가 충전하고 주문 결제 시 차감된다.
포인트 연산 규칙은 `Point` 값 객체가 캡슐화하며, `Member`는 이를 위임해 호출한다.

### 포인트 충전

관리자는 특정 회원 ID와 충전 금액을 지정해 포인트를 추가할 수 있다.  
충전 금액이 0 이하이면 `IllegalArgumentException`이 발생해 충전이 거부된다.  
존재하지 않는 회원 ID로 요청하면 `NoSuchElementException`(→ 404)이 발생한다.  
충전이 성공하면 회원의 포인트 잔액이 즉시 증가하며, `MemberCommandService.chargePoint()`가 `@Transactional`로 보호하므로 예외 발생 시 롤백된다.

### 포인트 차감

주문 생성 시 `상품 가격 × 주문 수량`만큼 포인트가 자동 차감된다.  
차감 금액이 0 이하이면 `IllegalArgumentException`이 발생한다.  
현재 잔액보다 차감 금액이 크면 `IllegalArgumentException("포인트가 부족합니다.")`가 발생해 주문이 실패한다.  
차감은 `OrderCommandService.createOrder()` 내 `deductPoints()` 헬퍼를 통해 처리되며, 메서드 전체가 `@Transactional`로 보호된다. 재고 차감 이후 포인트 부족 예외가 발생하면 재고도 함께 롤백된다.

### 불변 값 객체 패턴

`Point.charge()`와 `Point.deduct()`는 기존 객체를 변경하지 않고 **새 `Point` 인스턴스**를 반환한다.  
`Member`는 반환된 새 인스턴스로 필드를 교체(`this.point = this.point.charge(amount)`)한다.

### 잔액 초기화

포인트 잔액의 초기값은 0이며, `Point` 기본 생성자가 `value = 0`으로 초기화한다. DB 컬럼 기본값(`DEFAULT 0`)과 일치한다.

---

## 3. 데이터 모델

### DB 스키마 (V1__Initialize_project_tables.sql)

```sql
create table member
(
    id                 bigint auto_increment primary key,
    email              varchar(255) not null unique,
    password           varchar(255),
    kakao_access_token varchar(512),
    point              int          not null default 0
);
```

포인트 전용 테이블은 없다. `member` 테이블의 `point` 컬럼(INT, NOT NULL, DEFAULT 0)이 잔액을 관리한다.  
`@Embeddable`이지만 별도 테이블 없이 `member` 테이블 컬럼을 공유한다.

### 기본 데이터 (V2__Insert_default_data.sql)

| email | point |
|-------|-------|
| admin@example.com | 10,000,000 |
| user1@example.com | 5,000,000 |
| user2@example.com | 3,000,000 |

---

## 4. 도메인 객체

### Point.java (`gift.point`)

```java
@Embeddable
public class Point {
    @Column(name = "point")
    private int value;

    protected Point() { this.value = 0; }
    public Point(int value) { this.value = value; }

    public Point charge(int amount) {       // amount ≤ 0 → IllegalArgumentException
        return new Point(this.value + amount);
    }

    public Point deduct(int amount) {       // amount ≤ 0 또는 잔액 부족 → IllegalArgumentException
        return new Point(this.value - amount);
    }

    public int getValue() { return value; }
}
```

### Member.java (포인트 관련 부분)

```java
@Entity
public class Member {
    @Embedded
    private Point point = new Point(0);

    public void chargePoint(int amount) {
        this.point = this.point.charge(amount);  // 새 Point 인스턴스로 교체
    }

    public void deductPoint(int amount) {
        this.point = this.point.deduct(amount);  // 새 Point 인스턴스로 교체
    }

    public int getPoint() {
        return point.getValue();
    }
}
```

### 공개 API

| 메서드 | 위치 | 설명 |
|--------|------|------|
| `Point.charge(int amount)` | `Point` | 잔액에 amount를 더한 새 Point 반환. amount ≤ 0이면 예외 |
| `Point.deduct(int amount)` | `Point` | 잔액에서 amount를 뺀 새 Point 반환. amount ≤ 0 또는 잔액 부족이면 예외 |
| `Member.chargePoint(int amount)` | `Member` | Point.charge() 위임 후 필드 교체 |
| `Member.deductPoint(int amount)` | `Member` | Point.deduct() 위임 후 필드 교체 |
| `Member.getPoint()` | `Member` | 현재 포인트 잔액 반환 |

---

## 5. API 명세

| HTTP | 경로 | 동작 | 요청 | 성공 응답 |
|------|------|------|------|----------|
| POST | `/admin/members/{id}/charge-point` | 포인트 충전 | `amount` (form param, int) | 302 → `/admin/members` |

포인트 차감 전용 API는 없다. 차감은 `POST /api/orders` 주문 생성 흐름 내에서 내부적으로 발생한다.

`/admin/members/{id}/charge-point`는 관리자 UI 전용 엔드포인트로, JWT 인증 없이 접근 가능하다.

---

## 6. DTO 설계

충전 전용 DTO는 없다. 폼 파라미터를 `@RequestParam int amount`로 직접 수신한다.

```
POST /admin/members/{id}/charge-point
Content-Type: application/x-www-form-urlencoded

amount=1000
```

---

## 7. 다른 도메인과의 관계

```
Member (@Embedded Point)
  ▲ 충전: AdminMemberController → MemberCommandService → Member.chargePoint() → Point.charge()
  ▼ 차감: OrderCommandService(@Transactional) → Member.deductPoint() → Point.deduct()
              │
              └─ Option (price × quantity = 차감 금액 산출)
```

- `Point`는 `gift/point` 패키지의 독립 클래스이며 `@Embeddable`로 `member` 테이블 컬럼을 공유한다.
- 포인트 충전 경로(AdminMemberController → MemberCommandService)와 포인트 차감 경로(OrderCommandService → MemberRepository) 모두 `@Transactional`로 보호된다.
- `OrderCommandService.createOrder()`는 재고 차감 → 포인트 차감 → 위시 삭제 → 주문 저장을 단일 트랜잭션으로 처리하므로, 포인트 부족 시 재고도 롤백된다.
