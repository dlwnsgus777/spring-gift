package gift.order;

import static gift.support.UUIDGenerator.uuid;
import static org.assertj.core.api.Assertions.assertThat;

import gift.AbstractIntegrationTest;
import gift.category.CategoryRepository;
import gift.member.Member;
import gift.member.MemberRepository;
import gift.option.Option;
import gift.option.OptionRepository;
import gift.product.ProductRepository;
import gift.support.CategoryFixture;
import gift.support.MemberFixture;
import gift.support.OptionFixture;
import gift.support.OrderFixture;
import gift.support.ProductFixture;
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
        Member member = memberRepository.save(MemberFixture.builder().email("order_query_" + uuid() + "@test.com").build());
        Member otherMember = memberRepository.save(MemberFixture.builder().email("order_query_other_" + uuid() + "@test.com").build());

        Option option1 = savedOption();
        Option option2 = savedOption();
        Option option3 = savedOption();

        orderRepository.save(OrderFixture.builder(option1, member.getId()).quantity(1).message("주문1").build());
        orderRepository.save(OrderFixture.builder(option2, member.getId()).quantity(2).message("주문2").build());
        orderRepository.save(OrderFixture.builder(option3, otherMember.getId()).quantity(1).message("다른회원주문").build());

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
        var category = categoryRepository.save(CategoryFixture.builder().name("주문쿼리테스트_" + uuid()).build());
        var product = productRepository.save(ProductFixture.builder(category).name("주문쿼리상품_" + uuid()).build());
        return optionRepository.save(OptionFixture.builder(product).name("옵션_" + uuid()).build());
    }
}
