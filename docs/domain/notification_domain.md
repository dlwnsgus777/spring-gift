# 알림(Notification) 도메인 분석

> 작성일: 2026-05-15  
> 분석 대상 브랜치: feature/youth-6-fifth-assignment

---

## 1. 파일 구성

```
src/main/java/gift/notification/
├── MessageClient.java      — 알림 발송 인터페이스 (전략 패턴 추상화)
├── KakaoMessageClient.java — 카카오 나에게 메시지 발송 구현체
└── NotifySendService.java  — 알림 발송 가능 여부 판단 후 발송 위임
```

테스트 파일: 없음

---

## 2. 비즈니스 로직

알림 도메인은 주문 완료 후 회원에게 카카오 알림 메시지를 발송하는 책임을 갖는다.
이전에는 `KakaoMessageClient`가 `gift.order` 패키지에 위치했으나, 알림 관련 책임을 분리하기 위해 독립 패키지로 추출되었다.

### 알림 발송 조건 판단 (NotifySendService)

주문 완료 후 `OrderController`가 `NotifySendService.sendIfPossible(member, order)`를 호출한다.

- 회원의 `kakaoAccessToken`이 null이면 발송을 건너뛴다 (이메일/비밀번호 전용 가입 회원).
- `kakaoAccessToken`이 있으면 `MessageClient.sendToMe()`를 호출한다.
- 발송 중 예외가 발생하면 무시(`catch (Exception ignored)`)한다. 알림 실패는 주문 결과에 영향을 주지 않는다 (best-effort).

이 서비스는 `@Transactional` 외부에서 호출되어 알림 실패가 주문 트랜잭션 롤백을 유발하지 않는다.

### 카카오 나에게 메시지 발송 (KakaoMessageClient)

`MessageClient` 인터페이스를 구현한 `KakaoMessageClient`가 실제 HTTP 호출을 담당한다.

- `https://kapi.kakao.com/v2/api/talk/memo/default/send`로 POST 요청을 보낸다.
- `Authorization: Bearer {kakaoAccessToken}` 헤더로 인증한다.
- 메시지 내용: 상품명, 옵션명, 수량, 총 금액, 선물 메시지(있을 경우)를 포함한 텍스트 템플릿.
- `RestClient`로 HTTP 요청을 전송한다.

---

## 3. 도메인 객체

### MessageClient (인터페이스)

```java
public interface MessageClient {
    void sendToMe(String accessToken, Order order, Product product);
}
```

현재 구현체는 `KakaoMessageClient` 하나이지만, 인터페이스 분리로 다른 알림 채널 추가가 용이하다.

### NotifySendService

```java
@Service
public class NotifySendService {
    private final MessageClient kakaoMessageClient;

    public void sendIfPossible(Member member, Order order) {
        if (member.getKakaoAccessToken() == null) return;
        try {
            kakaoMessageClient.sendToMe(
                member.getKakaoAccessToken(),
                order,
                order.getOption().getProduct()
            );
        } catch (Exception ignored) {}
    }
}
```

### 메시지 템플릿

```
🎁 선물이 도착했어요!

{상품명} ({옵션명})
수량: {N}개
금액: {가격}원

💌 {선물 메시지}  ← 메시지가 있을 경우만 포함
```

---

## 4. 다른 도메인과의 관계

```
OrderController (트랜잭션 외부)
    └─► NotifySendService.sendIfPossible(member, order)
              │
              ├─ member.getKakaoAccessToken() == null → 건너뜀
              └─ KakaoMessageClient.sendToMe()
                        │
                        ├─ order.getOption().getProduct()  (Product 참조)
                        └─► Kakao API (kapi.kakao.com)
```

- `notification` 도메인은 `order`, `member`, `product` 도메인에 의존하지만, 역방향 의존은 없다.
- `Member.kakaoAccessToken`은 `auth` 도메인(KakaoAuthService)에서 저장되고, `notification` 도메인에서 읽힌다.
- 알림 발송은 항상 트랜잭션 커밋 이후 시도되며, 발송 실패가 주문 데이터 정합성에 영향을 주지 않는다.
