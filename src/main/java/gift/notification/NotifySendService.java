package gift.notification;

import gift.member.Member;
import gift.order.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotifySendService {
    private static final Logger log = LoggerFactory.getLogger(NotifySendService.class);

    private final MessageClient kakaoMessageClient;

    public NotifySendService(MessageClient kakaoMessageClient) {
        this.kakaoMessageClient = kakaoMessageClient;
    }

    public void sendIfPossible(Member member, Order order) {
        if (member.getKakaoAccessToken() == null) {
            return;
        }
        try {
            kakaoMessageClient.sendToMe(
                member.getKakaoAccessToken(),
                order,
                order.getOption().getProduct()
            );
        } catch (Exception e) {
            log.error("카카오 메시지 전송 실패 - memberId: {}, orderId: {}", member.getId(), order.getId(), e);
        }
    }
}
