package gift.support;

import gift.option.Option;
import gift.order.Order;

public class OrderFixture {
    public static Builder builder(Option option, Long memberId) {
        return new Builder(option, memberId);
    }

    public static class Builder {
        private final Option option;
        private final Long memberId;
        private int quantity = 1;
        private String message = null;

        private Builder(Option option, Long memberId) {
            this.option = option;
            this.memberId = memberId;
        }

        public Builder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Order build() {
            return new Order(option, memberId, quantity, message);
        }
    }
}
