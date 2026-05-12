package gift.product;

import gift.AbstractIntegrationTest;
import gift.category.Category;
import gift.category.CategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class ProductQueryServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ProductQueryService productQueryService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("존재하는 ID로 조회하면 해당 상품을 반환한다")
    void test01() {
        // arrange
        Category category = categoryRepository.save(
            new Category("쿼리테스트", "#FFFFFF", "http://img.com", null)
        );
        Product saved = productRepository.save(new Product("조회상품", 1000, "http://img.com", category));

        // act
        Product result = productQueryService.findById(saved.getId());

        // assert
        assertThat(result.getId()).isEqualTo(saved.getId());
        assertThat(result.getName()).isEqualTo("조회상품");
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회하면 NoSuchElementException을 던진다")
    void test02() {
        // arrange (id does not exist)

        // act & assert
        assertThatThrownBy(() -> productQueryService.findById(999999L))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("전체 상품 목록을 페이지로 반환한다")
    void test03() {
        // arrange - Flyway V2 시드 데이터: 상품 6개

        // act
        Page<Product> result = productQueryService.findAll(PageRequest.of(0, 10));

        // assert
        assertThat(result.getContent()).isNotEmpty();
    }
}
