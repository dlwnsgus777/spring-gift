package gift.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsByCategoryIdAndDeletedFalse(Long categoryId);

    Optional<Product> findByIdAndDeletedFalse(Long id);
    Page<Product> findAllByDeletedFalse(Pageable pageable);
    List<Product> findAllByDeletedFalse();
}
