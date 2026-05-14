package gift.support;

import gift.option.Option;
import gift.product.Product;

public class OptionFixture {
    public static Builder builder(Product product) {
        return new Builder(product);
    }

    public static class Builder {
        private final Product product;
        private String name = "옵션_" + UUIDGenerator.uuid();
        private int quantity = 100;

        private Builder(Product product) {
            this.product = product;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public Option build() {
            return new Option(product, name, quantity);
        }
    }
}
