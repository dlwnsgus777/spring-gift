package gift.wish;

import gift.common.ForbiddenException;
import gift.product.Product;
import gift.product.ProductQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WishCommandService {

    private final WishRepository wishRepository;
    private final ProductQueryService productQueryService;

    public WishCommandService(WishRepository wishRepository, ProductQueryService productQueryService) {
        this.wishRepository = wishRepository;
        this.productQueryService = productQueryService;
    }

    public WishResult addWish(Long memberId, Long productId) {
        Product product = productQueryService.findById(productId);
        return wishRepository.findByMemberIdAndProductId(memberId, product.getId())
            .map(existing -> new WishResult(existing, false))
            .orElseGet(() -> new WishResult(wishRepository.save(new Wish(memberId, product)), true));
    }

    public void removeWish(Long memberId, Long wishId) {
        Wish wish = wishRepository.findById(wishId).orElseThrow();
        if (!wish.getMemberId().equals(memberId)) {
            throw new ForbiddenException("Not the owner of this wish.");
        }
        wishRepository.delete(wish);
    }

    public void deleteByMemberIdAndProductId(Long memberId, Long productId) {
        wishRepository.findByMemberIdAndProductId(memberId, productId)
            .ifPresent(wishRepository::delete);
    }
}
