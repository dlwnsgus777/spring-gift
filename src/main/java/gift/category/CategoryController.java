package gift.category;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryQueryService categoryQueryService;
    private final CategoryCommandService categoryCommandService;

    public CategoryController(CategoryQueryService categoryQueryService, CategoryCommandService categoryCommandService) {
        this.categoryQueryService = categoryQueryService;
        this.categoryCommandService = categoryCommandService;
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getCategories() {
        List<CategoryResponse> response = categoryQueryService.findAll().stream()
            .map(CategoryResponse::from)
            .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        Category category = categoryCommandService.create(request);
        return ResponseEntity.created(URI.create("/api/categories/" + category.getId()))
            .body(CategoryResponse.from(category));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(
        @PathVariable Long id,
        @Valid @RequestBody CategoryRequest request
    ) {
        return ResponseEntity.ok(CategoryResponse.from(categoryCommandService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryCommandService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
