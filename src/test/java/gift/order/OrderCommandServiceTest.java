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
import gift.wish.Wish;
import gift.wish.WishRepository;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Autowired
    private WishRepository wishRepository;

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

    @Test
    @DisplayName("주문 완료 후 위시리스트에 있던 상품이 자동으로 삭제된다")
    void test05() {
        // arrange
        Option option = savedOption("주문위시삭제_" + uuid(), 1000, 10);
        Product product = option.getProduct();
        Member member = memberRepository.save(new Member("order_wish_del_" + uuid() + "@test.com", "pass"));
        member.chargePoint(10000);
        memberRepository.save(member);
        wishRepository.save(new Wish(member.getId(), product));

        // act
        orderCommandService.createOrder(member.getId(), option.getId(), 1, null);

        // assert
        assertThat(wishRepository.findByMemberIdAndProductId(member.getId(), product.getId())).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("재고가 1개인 옵션에 동시에 2건이 주문되면 1건만 성공하고 재고는 0이 된다")
    void test06() throws InterruptedException {
        // arrange — 재고 1개, 회원 2명 준비
        Option option = savedOption(1000, 1);
        Member member1 = memberRepository.save(new Member("lock_m1_" + uuid() + "@t.com", "p"));
        member1.chargePoint(100000);
        memberRepository.save(member1);
        Member member2 = memberRepository.save(new Member("lock_m2_" + uuid() + "@t.com", "p"));
        member2.chargePoint(100000);
        memberRepository.save(member2);

        int threadCount = 2;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        // 실패 원인을 구분: IllegalArgumentException(재고 부족) vs 기타(데드락 등 DB 오류)
        AtomicInteger illegalArgFailCount = new AtomicInteger(0);
        AtomicInteger unexpectedFailCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        Long optionId = option.getId();
        Long memberId1 = member1.getId();
        Long memberId2 = member2.getId();

        // act — 두 스레드가 동시에 같은 옵션 1개 주문
        executor.submit(() -> {
            ready.countDown();
            try {
                start.await();
                orderCommandService.createOrder(memberId1, optionId, 1, null);
                successCount.incrementAndGet();
            } catch (IllegalArgumentException e) {
                illegalArgFailCount.incrementAndGet();
            } catch (Exception e) {
                // 데드락, 낙관적 잠금 실패 등 예상치 못한 예외
                unexpectedFailCount.incrementAndGet();
            } finally {
                done.countDown();
            }
        });
        executor.submit(() -> {
            ready.countDown();
            try {
                start.await();
                orderCommandService.createOrder(memberId2, optionId, 1, null);
                successCount.incrementAndGet();
            } catch (IllegalArgumentException e) {
                illegalArgFailCount.incrementAndGet();
            } catch (Exception e) {
                // 데드락, 낙관적 잠금 실패 등 예상치 못한 예외
                unexpectedFailCount.incrementAndGet();
            } finally {
                done.countDown();
            }
        });

        ready.await();
        start.countDown();
        done.await();
        executor.shutdown();

        // assert — 정확히 1건만 성공하고, 나머지 1건은 재고 부족(IllegalArgumentException)으로 실패해야 한다
        // Pessimistic Lock 없이는:
        //   (a) 둘 다 성공(successCount=2, Lost Update) — 재고 부족 검증 실패
        //   (b) 데드락으로 한 건 실패(unexpectedFailCount=1) — 비즈니스 규칙이 아닌 DB 오류로 처리됨
        // Pessimistic Lock 적용 시: 정확히 1건 성공, 1건은 IllegalArgumentException(재고 부족)으로 실패
        assertThat(successCount.get())
            .as("재고 1개 옵션에 동시 주문 2건 중 정확히 1건만 성공해야 한다 (successCount=%d)", successCount.get())
            .isEqualTo(1);
        assertThat(unexpectedFailCount.get())
            .as("데드락 등 예상치 못한 DB 오류 없이 비즈니스 규칙(재고 부족)으로만 실패해야 한다 (unexpectedFailCount=%d)", unexpectedFailCount.get())
            .isEqualTo(0);

        // 재고는 0이어야 한다
        Option updated = optionRepository.findById(optionId).orElseThrow();
        assertThat(updated.getQuantity())
            .as("주문 후 남은 재고는 0이어야 한다")
            .isEqualTo(0);
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

    private Option savedOption(int price, int quantity) {
        return savedOption("주문동시성_" + uuid(), price, quantity);
    }

    private String uuid() {
        return UUID.randomUUID().toString().substring(0, 6);
    }
}
