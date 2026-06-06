package gift.product;

import gift.category.Category;
import gift.category.CategoryQueryService;
import gift.option.Option;
import gift.option.OptionRequest;
import gift.wish.WishRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductCommandService {

    private final ProductRepository productRepository;
    private final ProductQueryService productQueryService;
    private final CategoryQueryService categoryQueryService;
    private final WishRepository wishRepository;

    public ProductCommandService(
        ProductRepository productRepository,
        ProductQueryService productQueryService,
        CategoryQueryService categoryQueryService,
        WishRepository wishRepository
    ) {
        this.productRepository = productRepository;
        this.productQueryService = productQueryService;
        this.categoryQueryService = categoryQueryService;
        this.wishRepository = wishRepository;
    }

    public Product create(ProductRequest request) {
        validateKakao(request.name());
        return save(request);
    }

    public Product createForAdmin(ProductRequest request) {
        return save(request);
    }

    public Product update(Long id, ProductRequest request) {
        validateKakao(request.name());
        return applyUpdate(id, request);
    }

    public Product updateForAdmin(Long id, ProductRequest request) {
        return applyUpdate(id, request);
    }

    public void delete(Long id) {
        Product product = productQueryService.findById(id);
        wishRepository.deleteByProductId(id);
        product.delete();
    }

    public Option addOption(Long productId, OptionRequest request) {
        Product product = productQueryService.findById(productId);
        return product.addOption(request.name(), request.quantity());
    }

    public void removeOption(Long productId, Long optionId) {
        Product product = productQueryService.findById(productId);
        product.removeOption(optionId);
    }

    private Product save(ProductRequest request) {
        Category category = categoryQueryService.findById(request.categoryId());
        return productRepository.save(request.toEntity(category));
    }

    private Product applyUpdate(Long id, ProductRequest request) {
        Product product = productQueryService.findById(id);
        Category category = categoryQueryService.findById(request.categoryId());
        product.update(request.name(), request.price(), request.imageUrl(), category);
        return product;
    }

    private void validateKakao(String name) {
        if (name != null && name.contains("카카오")) {
            throw new IllegalArgumentException(
                "\"카카오\"가 포함된 상품명은 담당 MD와 협의한 경우에만 사용할 수 있습니다.");
        }
    }
}
