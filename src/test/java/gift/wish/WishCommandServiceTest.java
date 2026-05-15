package gift.wish;

import static gift.support.UUIDGenerator.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gift.AbstractIntegrationTest;
import gift.common.ForbiddenException;
import gift.category.CategoryRepository;
import gift.member.Member;
import gift.member.MemberRepository;
import gift.product.Product;
import gift.product.ProductRepository;
import gift.support.CategoryFixture;
import gift.support.MemberFixture;
import gift.support.ProductFixture;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class WishCommandServiceTest extends AbstractIntegrationTest {

    @Autowired
    private WishCommandService wishCommandService;

    @Autowired
    private WishRepository wishRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("새 상품을 위시에 추가하면 저장되고 isNew=true를 반환한다")
    void test01() {
        // arrange
        Member member = memberRepository.save(MemberFixture.builder().email("wish_cmd_" + uuid() + "@test.com").build());
        Product product = savedProduct();

        // act
        WishResult result = wishCommandService.addWish(member.getId(), product.getId());

        // assert
        assertThat(result.isNew()).isTrue();
        assertThat(result.wish().getId()).isNotNull();
        assertThat(result.wish().getMemberId()).isEqualTo(member.getId());
        assertThat(result.wish().getProduct().getId()).isEqualTo(product.getId());
    }

    @Test
    @DisplayName("이미 추가된 상품을 다시 추가하면 기존 위시를 반환하고 isNew=false다")
    void test02() {
        // arrange
        Member member = memberRepository.save(MemberFixture.builder().email("wish_cmd_dup_" + uuid() + "@test.com").build());
        Product product = savedProduct();
        WishResult firstResult = wishCommandService.addWish(member.getId(), product.getId());

        // act
        WishResult secondResult = wishCommandService.addWish(member.getId(), product.getId());

        // assert
        assertThat(secondResult.isNew()).isFalse();
        assertThat(secondResult.wish().getId()).isEqualTo(firstResult.wish().getId());
    }

    @Test
    @DisplayName("본인 위시를 삭제하면 위시가 제거된다")
    void test03() {
        // arrange
        Member member = memberRepository.save(MemberFixture.builder().email("wish_cmd_del_" + uuid() + "@test.com").build());
        Product product = savedProduct();
        WishResult result = wishCommandService.addWish(member.getId(), product.getId());
        Long wishId = result.wish().getId();

        // act
        wishCommandService.removeWish(member.getId(), wishId);

        // assert
        assertThat(wishRepository.findById(wishId)).isEmpty();
    }

    @Test
    @DisplayName("다른 회원의 위시를 삭제하려 하면 ForbiddenException을 던진다")
    void test04() {
        // arrange
        Member owner = memberRepository.save(MemberFixture.builder().email("wish_cmd_owner_" + uuid() + "@test.com").build());
        Member other = memberRepository.save(MemberFixture.builder().email("wish_cmd_other_" + uuid() + "@test.com").build());
        Product product = savedProduct();
        WishResult result = wishCommandService.addWish(owner.getId(), product.getId());
        Long wishId = result.wish().getId();

        // act & assert
        assertThatThrownBy(() -> wishCommandService.removeWish(other.getId(), wishId))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("존재하지 않는 위시 ID로 삭제하면 NoSuchElementException을 던진다")
    void test05() {
        // arrange
        Member member = memberRepository.save(MemberFixture.builder().email("wish_cmd_notfound_" + uuid() + "@test.com").build());
        Long nonExistentWishId = Long.MAX_VALUE;

        // act & assert
        assertThatThrownBy(() -> wishCommandService.removeWish(member.getId(), nonExistentWishId))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("위시에 있는 상품을 memberId+productId로 삭제하면 위시가 제거된다")
    void test06() {
        // arrange
        Member member = memberRepository.save(MemberFixture.builder().email("wish_cmd_byprod_" + uuid() + "@test.com").build());
        Product product = savedProduct();
        wishCommandService.addWish(member.getId(), product.getId());

        // act
        wishCommandService.deleteByMemberIdAndProductId(member.getId(), product.getId());

        // assert
        assertThat(wishRepository.findByMemberIdAndProductId(member.getId(), product.getId())).isEmpty();
    }

    @Test
    @DisplayName("위시에 없는 상품을 memberId+productId로 삭제해도 예외가 발생하지 않는다")
    void test07() {
        // arrange
        Member member = memberRepository.save(MemberFixture.builder().email("wish_cmd_notwish_" + uuid() + "@test.com").build());
        Product product = savedProduct();

        // act & assert
        assertThatNoException().isThrownBy(
            () -> wishCommandService.deleteByMemberIdAndProductId(member.getId(), product.getId())
        );
    }

    private Product savedProduct() {
        var category = categoryRepository.save(CategoryFixture.builder().name("위시커맨드테스트_" + uuid()).build());
        return productRepository.save(ProductFixture.builder(category).name("위시커맨드상품_" + uuid()).build());
    }
}
