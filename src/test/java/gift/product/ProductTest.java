package gift.product;

import gift.category.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    private static final Category DUMMY_CATEGORY =
        new Category("테스트", "#FFFFFF", "http://img.com", null);

    @Test
    @DisplayName("유효한 이름으로 Product를 생성한다")
    void test01() {
        // arrange & act
        Product product = new Product("유효한이름", 1000, "http://img.com", DUMMY_CATEGORY);

        // assert
        assertThat(product.getName()).isEqualTo("유효한이름");
    }

    @Test
    @DisplayName("이름이 15자를 초과하면 IllegalArgumentException을 던진다")
    void test02() {
        // arrange
        String longName = "a".repeat(16);

        // act & assert
        assertThatThrownBy(() -> new Product(longName, 1000, "http://img.com", DUMMY_CATEGORY))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("허용되지 않는 특수문자가 포함되면 IllegalArgumentException을 던진다")
    void test03() {
        // arrange
        String invalidName = "상품!@#";

        // act & assert
        assertThatThrownBy(() -> new Product(invalidName, 1000, "http://img.com", DUMMY_CATEGORY))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("이름이 null이면 IllegalArgumentException을 던진다")
    void test04() {
        // arrange & act & assert
        assertThatThrownBy(() -> new Product(null, 1000, "http://img.com", DUMMY_CATEGORY))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
