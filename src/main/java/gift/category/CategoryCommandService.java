package gift.category;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CategoryCommandService {

    private final CategoryRepository categoryRepository;

    public CategoryCommandService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Category create(CategoryRequest request) {
        return categoryRepository.save(request.toEntity());
    }

    public Category update(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id).orElseThrow();
        category.update(request.name(), request.color(), request.imageUrl(), request.description());
        return category;
    }

    public void delete(Long id) {
        categoryRepository.deleteById(id);
    }
}
