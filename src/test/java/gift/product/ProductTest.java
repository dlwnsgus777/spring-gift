package gift.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gift.category.Category;
import gift.support.CategoryFixture;
import gift.support.ProductFixture;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductTest {

    private static final Category DUMMY_CATEGORY = CategoryFixture.builder().name("테스트").build();

    @Test
    @DisplayName("유효한 이름으로 Product를 생성한다")
    void test01() {
        // arrange & act
        Product product = ProductFixture.builder(DUMMY_CATEGORY).name("유효한이름").build();

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

    @Test
    @DisplayName("유효한 이름으로 옵션을 추가하면 options 컬렉션에 추가된다")
    void test05() {
        // arrange
        Product product = ProductFixture.builder(DUMMY_CATEGORY).name("상품이름").build();
        int beforeSize = product.getOptions().size();

        // act
        product.addOption("옵션이름", 10);

        // assert
        assertThat(product.getOptions()).hasSize(beforeSize + 1);
        assertThat(product.getOptions().get(0).getName()).isEqualTo("옵션이름");
    }

    @Test
    @DisplayName("같은 이름으로 옵션을 추가하면 IllegalArgumentException을 던진다")
    void test06() {
        // arrange
        Product product = ProductFixture.builder(DUMMY_CATEGORY).name("상품이름").build();
        product.addOption("중복옵션", 10);

        // act & assert
        assertThatThrownBy(() -> product.addOption("중복옵션", 5))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("옵션이 2개일 때 removeOption을 호출하면 해당 옵션이 제거된다")
    void test07() {
        // arrange
        Product product = ProductFixture.builder(DUMMY_CATEGORY).name("상품이름").build();
        product.addOption("옵션A", 10);
        product.addOption("옵션B", 5);
        int beforeSize = product.getOptions().size();
        // DB 없는 단위 테스트에서 Option.id는 항상 null이므로 null로 조회
        Long targetId = null;

        // act
        product.removeOption(targetId);

        // assert
        assertThat(product.getOptions()).hasSize(beforeSize - 1);
    }

    @Test
    @DisplayName("옵션이 1개일 때 removeOption을 호출하면 IllegalArgumentException을 던진다")
    void test08() {
        // arrange
        Product product = ProductFixture.builder(DUMMY_CATEGORY).name("상품이름").build();
        product.addOption("유일한옵션", 10);
        // DB 없는 단위 테스트에서 Option.id는 항상 null이므로 null로 조회
        Long targetId = null;

        // act & assert
        assertThatThrownBy(() -> product.removeOption(targetId))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("존재하지 않는 optionId로 removeOption을 호출하면 NoSuchElementException을 던진다")
    void test09() {
        // arrange
        Product product = ProductFixture.builder(DUMMY_CATEGORY).name("상품이름").build();
        product.addOption("옵션이름", 10);
        // null id 옵션이 있는 상태에서 999L로 조회하면 Objects.equals(null, 999L) = false → 못 찾음
        Long nonExistentId = 999L;

        // act & assert
        assertThatThrownBy(() -> product.removeOption(nonExistentId))
            .isInstanceOf(NoSuchElementException.class);
    }
}
