package gift.support;

import gift.product.Product;
import gift.wish.Wish;

public class WishFixture {
    public static Builder builder(Long memberId, Product product) {
        return new Builder(memberId, product);
    }

    public static class Builder {
        private final Long memberId;
        private final Product product;

        private Builder(Long memberId, Product product) {
            this.memberId = memberId;
            this.product = product;
        }

        public Wish build() {
            return new Wish(memberId, product);
        }
    }
}
