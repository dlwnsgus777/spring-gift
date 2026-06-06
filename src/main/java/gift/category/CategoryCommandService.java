package gift.category;

import gift.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@Transactional
public class CategoryCommandService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryCommandService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    public Category create(CategoryRequest request) {
        return categoryRepository.save(request.toEntity());
    }

    public Category update(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("카테고리가 존재하지 않습니다. id=" + id));
        category.update(request.name(), request.color(), request.imageUrl(), request.description());
        return category;
    }

    public void delete(Long id) {
        if (productRepository.existsByCategoryIdAndDeletedFalse(id)) {
            throw new CategoryHasProductsException("카테고리에 연결된 상품이 있습니다. id=" + id);
        }
        categoryRepository.deleteById(id);
    }
}
