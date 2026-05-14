package gift.notification;

import gift.member.Member;
import gift.order.Order;
import org.springframework.stereotype.Service;

@Service
public class NotifySendService {
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
        } catch (Exception ignored) {
        }
    }
}
