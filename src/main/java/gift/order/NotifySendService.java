package gift.order;

import gift.member.Member;
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
