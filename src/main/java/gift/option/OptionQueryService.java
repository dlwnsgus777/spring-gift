package gift.option;

import gift.product.Product;
import gift.product.ProductQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class OptionQueryService {

    private final ProductQueryService productQueryService;

    public OptionQueryService(ProductQueryService productQueryService) {
        this.productQueryService = productQueryService;
    }

    public List<Option> findByProductId(Long productId) {
        Product product = productQueryService.findById(productId);
        return product.getOptions();
    }
}
