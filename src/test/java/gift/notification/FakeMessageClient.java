package gift.notification;

import gift.order.Order;
import gift.product.Product;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class FakeMessageClient implements MessageClient {
    private int sendCount = 0;

    @Override
    public void sendToMe(String accessToken, Order order, Product product) {
        sendCount++;
    }

    public int getSendCount() {
        return sendCount;
    }

    public void reset() {
        sendCount = 0;
    }
}
