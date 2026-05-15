package gift.notification;

import gift.order.Order;
import gift.product.Product;

public interface MessageClient {
    void sendToMe(String accessToken, Order order, Product product);
}
