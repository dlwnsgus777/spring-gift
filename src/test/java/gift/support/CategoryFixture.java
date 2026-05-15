package gift.support;

import gift.category.Category;

public class CategoryFixture {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name = "카테고리_" + UUIDGenerator.uuid();
        private String color = "#FFFFFF";
        private String imageUrl = "http://img.com";
        private String description = null;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder color(String color) {
            this.color = color;
            return this;
        }

        public Builder imageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Category build() {
            return new Category(name, color, imageUrl, description);
        }
    }
}
