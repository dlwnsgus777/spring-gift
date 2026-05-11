# 포인트 도메인 분석

> 작성일: 2026-05-11  
> 분석 대상 브랜치: feature/youth-6-fifth-assignment

---

## 1. 파일 구성

```
src/main/java/gift/member/
├── Member.java              — 포인트 필드(point) + chargePoint() / deductPoint() 비즈니스 로직
├── MemberCommandService.java — chargePoint(id, amount) 트랜잭션 처리
├── MemberRepository.java    — 포인트 전용 메서드 없음, JpaRepository 기본 save() 활용
└── AdminMemberController.java — POST /{id}/charge-point 엔드포인트 (Admin UI)

※ gift/point 패키지는 현재 존재하지 않음.
  포인트 충전 로직은 member 패키지에, 포인트 차감 로직은 OrderController에 직접 위치.
```

**테스트 파일:**
- `src/test/java/gift/member/AdminMemberControllerTest.java` — `test06`: POST /{id}/charge-point 통합 테스트
- `src/test/java/gift/member/MemberCommandServiceTest.java` — `test06`, `test07`: chargePoint 성공·실패 단위 테스트

---

## 2. 비즈니스 로직

포인트는 `Member` 엔티티가 보유한 잔액으로, 관리자가 충전하고 주문 결제 시 차감된다.

### 포인트 충전

관리자는 특정 회원 ID와 충전 금액을 지정해 포인트를 추가할 수 있다.  
충전 금액이 0 이하이면 `IllegalArgumentException`이 발생해 충전이 거부된다.  
존재하지 않는 회원 ID로 요청하면 `NoSuchElementException`(→ 404)이 발생한다.  
충전이 성공하면 회원의 포인트 잔액이 즉시 증가하며, `MemberCommandService.chargePoint()`가 `@Transactional`로 보호하므로 예외 발생 시 롤백된다.

### 포인트 차감

주문 생성 시 상품 가격 × 주문 수량만큼 포인트가 자동 차감된다.  
차감 금액이 0 이하이면 `IllegalArgumentException`이 발생한다.  
현재 잔액보다 차감 금액이 크면 `IllegalArgumentException("포인트가 부족합니다.")`가 발생해 주문이 실패한다.  
차감은 현재 `OrderController`가 `member.deductPoint(price)` 후 `memberRepository.save(member)`를 직접 호출하는 방식으로 처리된다.  
`OrderController`에는 `@Transactional`이 없으므로, 재고 차감 후 포인트 부족 예외가 발생해도 재고가 롤백되지 않는 버그가 존재한다.

### 잔액 초기화

포인트 잔액의 초기값은 0이며, 신규 회원 생성 시 별도 설정 없이 DB 기본값(`DEFAULT 0`)이 적용된다.

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

### 기본 데이터 (V2__Insert_default_data.sql)

| email | point |
|-------|-------|
| admin@example.com | 10,000,000 |
| user1@example.com | 5,000,000 |
| user2@example.com | 3,000,000 |

---

## 4. 도메인 객체

```java
// Member.java (포인트 관련 부분)
@Entity
public class Member {
    private int point;  // NOT NULL, DEFAULT 0

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
}
```

### 공개 API

| 메서드 | 설명 |
|--------|------|
| `chargePoint(int amount)` | 잔액에 amount를 더한다. amount ≤ 0이면 예외 |
| `deductPoint(int amount)` | 잔액에서 amount를 뺀다. amount ≤ 0 또는 잔액 부족이면 예외 |
| `getPoint()` | 현재 포인트 잔액 반환 |

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
Member (point 보유)
  ▲ 충전: AdminMemberController → MemberCommandService → Member.chargePoint()
  ▼ 차감: OrderController → MemberRepository → Member.deductPoint()
              │
              └─ Option (price × quantity = 차감 금액 산출)
```

- `Member.point`는 `gift/member` 패키지에 완전히 내장되어 있다. 포인트 도메인 독립 패키지(`gift/point`)는 현재 존재하지 않는다.
- 포인트 충전 경로(AdminMemberController → MemberCommandService)와 포인트 차감 경로(OrderController → MemberRepository 직접)가 서로 다른 계층 구조를 가진다.
- `OrderController`가 `MemberRepository`를 직접 주입받아 포인트를 차감하므로, Order 도메인이 Member 도메인의 Repository에 직접 의존하는 상태다.
