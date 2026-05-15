package gift.option;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gift.AbstractIntegrationTest;
import gift.category.CategoryRepository;
import gift.product.Product;
import gift.product.ProductCommandService;
import gift.product.ProductRepository;
import gift.support.CategoryFixture;
import gift.support.ProductFixture;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class OptionQueryServiceTest extends AbstractIntegrationTest {

    @Autowired
    private OptionQueryService optionQueryService;

    @Autowired
    private ProductCommandService productCommandService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("상품이 존재하면 해당 상품의 옵션 목록을 반환한다")
    void test01() {
        // arrange
        var category = categoryRepository.save(CategoryFixture.builder().name("옵션쿼리테스트").build());
        Product product = productRepository.save(ProductFixture.builder(category).name("테스트상품").build());
        productCommandService.addOption(product.getId(), new OptionRequest("옵션A", 10));
        productCommandService.addOption(product.getId(), new OptionRequest("옵션B", 5));

        // act
        List<Option> result = optionQueryService.findByProductId(product.getId());

        // assert
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Option::getName).contains("옵션A", "옵션B");
    }

    @Test
    @DisplayName("상품이 존재하지 않으면 NoSuchElementException을 던진다")
    void test02() {
        // arrange (id does not exist)

        // act & assert
        assertThatThrownBy(() -> optionQueryService.findByProductId(999999L))
            .isInstanceOf(NoSuchElementException.class);
    }
}
