package gift.product;

import gift.AbstractIntegrationTest;
import gift.category.Category;
import gift.category.CategoryRepository;
import gift.option.Option;
import gift.option.OptionRepository;
import gift.option.OptionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class ProductCommandServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ProductCommandService productCommandService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private OptionRepository optionRepository;

    @Test
    @DisplayName("유효한 요청으로 상품을 생성하면 저장된 상품을 반환한다")
    void test01() {
        // arrange
        Category category = categoryRepository.save(
            new Category("커맨드테스트", "#FFFFFF", "http://img.com", null)
        );
        ProductRequest request = new ProductRequest("새상품", 5000, "http://img.com", category.getId());

        // act
        Product result = productCommandService.create(request);

        // assert
        assertThat(result.getId()).isNotNull();
        assertThat(result.getName()).isEqualTo("새상품");
        assertThat(productRepository.findById(result.getId())).isPresent();
    }

    @Test
    @DisplayName("존재하지 않는 카테고리 ID로 생성하면 NoSuchElementException을 던진다")
    void test02() {
        // arrange
        ProductRequest request = new ProductRequest("상품이름", 1000, "http://img.com", 999999L);

        // act & assert
        assertThatThrownBy(() -> productCommandService.create(request))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("카카오가 포함된 이름으로 생성하면 IllegalArgumentException을 던진다")
    void test03() {
        // arrange
        Category category = categoryRepository.save(
            new Category("카카오카테고리", "#FFFFFF", "http://img.com", null)
        );
        ProductRequest request = new ProductRequest("카카오상품", 1000, "http://img.com", category.getId());

        // act & assert
        assertThatThrownBy(() -> productCommandService.create(request))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("상품을 수정하면 변경된 내용이 반영된다")
    void test04() {
        // arrange
        Category category = categoryRepository.save(
            new Category("수정카테고리", "#FFFFFF", "http://img.com", null)
        );
        Product saved = productRepository.save(new Product("수정전", 1000, "http://img.com", category));
        ProductRequest request = new ProductRequest("수정후", 2000, "http://new.com", category.getId());

        // act
        Product result = productCommandService.update(saved.getId(), request);

        // assert
        assertThat(result.getName()).isEqualTo("수정후");
        assertThat(result.getPrice()).isEqualTo(2000);
    }

    @Test
    @DisplayName("존재하지 않는 상품을 수정하면 NoSuchElementException을 던진다")
    void test05() {
        // arrange
        Category category = categoryRepository.save(
            new Category("수정없는카테고리", "#FFFFFF", "http://img.com", null)
        );
        ProductRequest request = new ProductRequest("이름", 1000, "http://img.com", category.getId());

        // act & assert
        assertThatThrownBy(() -> productCommandService.update(999999L, request))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("상품을 삭제하면 저장소에서 제거된다")
    void test06() {
        // arrange
        Category category = categoryRepository.save(
            new Category("삭제카테고리", "#FFFFFF", "http://img.com", null)
        );
        Product saved = productRepository.save(new Product("삭제상품", 1000, "http://img.com", category));

        // act
        productCommandService.delete(saved.getId());

        // assert
        assertThat(productRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("유효한 요청으로 옵션을 추가하면 저장된 Option을 반환한다")
    void test07() {
        // arrange
        Category category = categoryRepository.save(
            new Category("옵션추가카테고리", "#FFFFFF", "http://img.com", null)
        );
        Product product = productRepository.save(new Product("옵션추가상품", 1000, "http://img.com", category));
        OptionRequest request = new OptionRequest("옵션A", 10);

        // act
        Option result = productCommandService.addOption(product.getId(), request);

        // assert
        assertThat(result.getName()).isEqualTo("옵션A");
        assertThat(optionRepository.findByProductId(product.getId())).hasSize(1);
    }

    @Test
    @DisplayName("존재하지 않는 상품 ID로 옵션을 추가하면 NoSuchElementException을 던진다")
    void test08() {
        // arrange
        OptionRequest request = new OptionRequest("옵션B", 5);

        // act & assert
        assertThatThrownBy(() -> productCommandService.addOption(999999L, request))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("같은 상품 내 중복 이름으로 옵션을 추가하면 IllegalArgumentException을 던진다")
    void test09() {
        // arrange
        Category category = categoryRepository.save(
            new Category("중복옵션카테고리", "#FFFFFF", "http://img.com", null)
        );
        Product product = productRepository.save(new Product("중복옵션상품", 1000, "http://img.com", category));
        productCommandService.addOption(product.getId(), new OptionRequest("중복옵션", 10));
        OptionRequest request = new OptionRequest("중복옵션", 5);

        // act & assert
        assertThatThrownBy(() -> productCommandService.addOption(product.getId(), request))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("옵션이 2개 이상일 때 옵션을 삭제하면 저장소에서 제거된다")
    void test10() {
        // arrange
        Category category = categoryRepository.save(
            new Category("삭제옵션카테고리", "#FFFFFF", "http://img.com", null)
        );
        Product product = productRepository.save(new Product("삭제옵션상품", 1000, "http://img.com", category));
        Option option1 = productCommandService.addOption(product.getId(), new OptionRequest("옵션X", 10));
        productCommandService.addOption(product.getId(), new OptionRequest("옵션Y", 20));

        // act
        productCommandService.removeOption(product.getId(), option1.getId());

        // assert
        assertThat(optionRepository.findByProductId(product.getId())).hasSize(1);
    }

    @Test
    @DisplayName("마지막 옵션을 삭제하려 하면 IllegalArgumentException을 던진다")
    void test11() {
        // arrange
        Category category = categoryRepository.save(
            new Category("마지막옵션카테고리", "#FFFFFF", "http://img.com", null)
        );
        Product product = productRepository.save(new Product("마지막옵션상품", 1000, "http://img.com", category));
        Option onlyOption = productCommandService.addOption(product.getId(), new OptionRequest("유일옵션", 10));

        // act & assert
        assertThatThrownBy(() -> productCommandService.removeOption(product.getId(), onlyOption.getId()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
