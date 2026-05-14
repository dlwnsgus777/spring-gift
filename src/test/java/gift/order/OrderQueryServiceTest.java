package gift.order;

import static org.assertj.core.api.Assertions.assertThat;

import gift.AbstractIntegrationTest;
import gift.category.Category;
import gift.category.CategoryRepository;
import gift.member.Member;
import gift.member.MemberRepository;
import gift.option.Option;
import gift.option.OptionRepository;
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
class OrderQueryServiceTest extends AbstractIntegrationTest {

    @Autowired
    private OrderQueryService orderQueryService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private OptionRepository optionRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("회원 ID로 주문 목록을 조회하면 해당 회원의 주문만 반환한다")
    void test01() {
        // arrange
        Member member = memberRepository.save(new Member("order_query_" + uuid() + "@test.com", "pass"));
        Member otherMember = memberRepository.save(new Member("order_query_other_" + uuid() + "@test.com", "pass"));

        Option option1 = savedOption();
        Option option2 = savedOption();
        Option option3 = savedOption();

        orderRepository.save(new Order(option1, member.getId(), 1, "주문1"));
        orderRepository.save(new Order(option2, member.getId(), 2, "주문2"));
        orderRepository.save(new Order(option3, otherMember.getId(), 1, "다른회원주문"));

        Pageable pageable = PageRequest.of(0, 10);

        // act
        Page<Order> result = orderQueryService.findByMemberId(member.getId(), pageable);

        // assert
        assertThat(result.getContent())
            .extracting(Order::getMemberId)
            .containsOnly(member.getId());
        assertThat(result.getContent()).hasSize(2);
    }

    private Option savedOption() {
        Category category = categoryRepository.save(
            new Category("주문쿼리테스트_" + uuid(), "#FFFFFF", "http://img.com", null)
        );
        Product product = productRepository.save(
            new Product("주문쿼리상품_" + uuid(), 1000, "http://img.com", category)
        );
        return optionRepository.save(new Option(product, "옵션_" + uuid(), 100));
    }

    private String uuid() {
        return UUID.randomUUID().toString().substring(0, 6);
    }
}
