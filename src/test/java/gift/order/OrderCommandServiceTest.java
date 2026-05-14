package gift.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class OrderCommandServiceTest extends AbstractIntegrationTest {

    @Autowired
    private OrderCommandService orderCommandService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OptionRepository optionRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("포인트와 재고가 충분하면 주문이 생성되고 재고·포인트가 차감된다")
    void test01() {
        // arrange
        Option option = savedOption("주문커맨드상품_" + uuid(), 1000, 10);
        Member member = memberRepository.save(new Member("order_cmd_" + uuid() + "@test.com", "pass"));
        member.chargePoint(10000);
        memberRepository.save(member);

        // act
        Order order = orderCommandService.createOrder(member.getId(), option.getId(), 2, "테스트");

        // assert
        assertThat(order.getId()).isNotNull();
        assertThat(order.getQuantity()).isEqualTo(2);

        Option updatedOption = optionRepository.findById(option.getId()).get();
        assertThat(updatedOption.getQuantity()).isEqualTo(8);

        Member updatedMember = memberRepository.findById(member.getId()).get();
        assertThat(updatedMember.getPoint()).isEqualTo(8000);
    }

    @Test
    @DisplayName("재고보다 많은 수량으로 주문하면 IllegalArgumentException이 발생한다")
    void test02() {
        // arrange
        Option option = savedOption("주문재고부족_" + uuid(), 1000, 2);
        Member member = memberRepository.save(new Member("order_cmd_stock_" + uuid() + "@test.com", "pass"));
        member.chargePoint(100000);
        memberRepository.save(member);

        // act + assert
        assertThatThrownBy(() -> orderCommandService.createOrder(member.getId(), option.getId(), 10, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("포인트가 부족하면 IllegalArgumentException이 발생한다")
    void test03() {
        // arrange
        Option option = savedOption("주문포인트부족_" + uuid(), 100000, 10);
        Member member = memberRepository.save(new Member("order_cmd_point_" + uuid() + "@test.com", "pass"));

        // act + assert
        assertThatThrownBy(() -> orderCommandService.createOrder(member.getId(), option.getId(), 1, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("포인트가 부족하면 재고가 차감되지 않는다")
    void test04() {
        // arrange
        Option option = savedOption("주문롤백테스트_" + uuid(), 10000, 5);
        Member member = memberRepository.save(new Member("order_rollback_" + uuid() + "@test.com", "pass"));
        // member has 0 points — insufficient for price 10000 * 1

        // act
        assertThatThrownBy(() -> orderCommandService.createOrder(member.getId(), option.getId(), 1, null))
            .isInstanceOf(IllegalArgumentException.class);

        // assert
        Option fetchedOption = optionRepository.findById(option.getId()).get();
        assertThat(fetchedOption.getQuantity()).isEqualTo(5);
    }

    private Option savedOption(String productName, int price, int quantity) {
        Category category = categoryRepository.save(
            new Category("주문커맨드카테고리_" + uuid(), "#FFFFFF", "http://img.com", null)
        );
        Product product = productRepository.save(
            new Product(productName, price, "http://img.com", category)
        );
        return optionRepository.save(new Option(product, "기본옵션_" + uuid(), quantity));
    }

    private String uuid() {
        return UUID.randomUUID().toString().substring(0, 6);
    }
}
