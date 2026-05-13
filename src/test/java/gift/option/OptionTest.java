package gift.option;

import gift.category.Category;
import gift.product.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionTest {

    private static final Category DUMMY_CATEGORY =
        new Category("테스트", "#FFFFFF", "http://img.com", null);

    private static final Product DUMMY_PRODUCT =
        new Product("유효한상품", 1000, "http://img.com", DUMMY_CATEGORY);

    @Test
    @DisplayName("유효한 이름으로 Option을 생성한다")
    void test01() {
        // arrange
        String validName = "유효한옵션이름";

        // act
        Option option = new Option(DUMMY_PRODUCT, validName, 10);

        // assert
        assertThat(option.getName()).isEqualTo(validName);
    }

    @Test
    @DisplayName("이름이 50자를 초과하면 IllegalArgumentException을 던진다")
    void test02() {
        // arrange
        String longName = "a".repeat(51);

        // act & assert
        assertThatThrownBy(() -> new Option(DUMMY_PRODUCT, longName, 10))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("허용되지 않는 특수문자가 포함되면 IllegalArgumentException을 던진다")
    void test03() {
        // arrange
        String invalidName = "옵션!@#";

        // act & assert
        assertThatThrownBy(() -> new Option(DUMMY_PRODUCT, invalidName, 10))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("이름이 null이면 IllegalArgumentException을 던진다")
    void test04() {
        // arrange & act & assert
        assertThatThrownBy(() -> new Option(DUMMY_PRODUCT, null, 10))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("이름이 공백이면 IllegalArgumentException을 던진다")
    void test05() {
        // arrange
        String blankName = "   ";

        // act & assert
        assertThatThrownBy(() -> new Option(DUMMY_PRODUCT, blankName, 10))
            .isInstanceOf(IllegalArgumentException.class);
    }

}
