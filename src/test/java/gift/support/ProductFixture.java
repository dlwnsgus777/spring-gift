package gift.support;

import gift.category.Category;
import gift.product.Product;

public class ProductFixture {
    public static Builder builder(Category category) {
        return new Builder(category);
    }

    public static class Builder {
        private final Category category;
        private String name = "상품_" + UUIDGenerator.uuid();
        private int price = 1000;
        private String imageUrl = "http://img.com";

        private Builder(Category category) {
            this.category = category;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder price(int price) {
            this.price = price;
            return this;
        }

        public Builder imageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
            return this;
        }

        public Product build() {
            return new Product(name, price, imageUrl, category);
        }
    }
}
