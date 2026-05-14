package gift.wish;

import static org.assertj.core.api.Assertions.assertThat;

import gift.AbstractIntegrationTest;
import gift.category.Category;
import gift.category.CategoryRepository;
import gift.member.Member;
import gift.member.MemberRepository;
import gift.product.Product;
import gift.product.ProductRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class WishQueryServiceTest extends AbstractIntegrationTest {

    @Autowired
    private WishQueryService wishQueryService;

    @Autowired
    private WishRepository wishRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("회원 ID로 위시리스트를 조회하면 해당 회원의 위시만 반환한다")
    void test01() {
        // arrange
        Member member = memberRepository.save(new Member("wish_query_" + uuid() + "@test.com", "pass"));
        Product product1 = savedProduct();
        Product product2 = savedProduct();
        wishRepository.save(new Wish(member.getId(), product1));
        wishRepository.save(new Wish(member.getId(), product2));
        Pageable pageable = PageRequest.of(0, 10);

        // act
        Page<Wish> result = wishQueryService.findByMemberId(member.getId(), pageable);

        // assert
        assertThat(result.getContent())
            .extracting(wish -> wish.getProduct().getId())
            .containsExactlyInAnyOrder(product1.getId(), product2.getId());
    }

    @Test
    @DisplayName("위시가 없는 회원 ID로 조회하면 빈 페이지를 반환한다")
    void test02() {
        // arrange
        Member member = memberRepository.save(new Member("wish_query_empty_" + uuid() + "@test.com", "pass"));
        Pageable pageable = PageRequest.of(0, 10);

        // act
        Page<Wish> result = wishQueryService.findByMemberId(member.getId(), pageable);

        // assert
        assertThat(result.getContent()).isEmpty();
    }

    private Product savedProduct() {
        Category category = categoryRepository.save(
            new Category("위시쿼리테스트_" + uuid(), "#FFFFFF", "http://img.com", null)
        );
        return productRepository.save(
            new Product("위시쿼리상품_" + uuid(), 1000, "http://img.com", category)
        );
    }

    private String uuid() {
        return UUID.randomUUID().toString().substring(0, 6);
    }
}
