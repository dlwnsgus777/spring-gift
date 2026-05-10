package gift.category;

import gift.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class CategoryCommandServiceTest extends AbstractIntegrationTest {

    @Autowired
    private CategoryCommandService categoryCommandService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("카테고리를 생성하고 저장된 결과를 반환한다")
    void test01() {
        // arrange
        CategoryRequest request = new CategoryRequest("신규카테고리", "#FFFFFF", "http://img.url", null);

        // act
        Category result = categoryCommandService.create(request);

        // assert
        assertThat(result.getName()).isEqualTo("신규카테고리");
        assertThat(result.getId()).isNotNull();
        assertThat(categoryRepository.findById(result.getId())).isPresent();
    }

    @Test
    @DisplayName("존재하는 카테고리를 수정하면 변경된 내용을 반환한다")
    void test02() {
        // arrange - Flyway V2 시드 데이터에서 첫 번째 카테고리 사용
        Long existingId = categoryRepository.findAll().get(0).getId();
        CategoryRequest request = new CategoryRequest("디지털기기", "#0000FF", "http://new.url", "변경된 설명");

        // act
        Category result = categoryCommandService.update(existingId, request);

        // assert
        assertThat(result.getName()).isEqualTo("디지털기기");
        assertThat(result.getColor()).isEqualTo("#0000FF");
    }

    @Test
    @DisplayName("존재하지 않는 카테고리를 수정하면 NoSuchElementException을 던진다")
    void test03() {
        // arrange
        CategoryRequest request = new CategoryRequest("이름", "#FFFFFF", "http://img.url", null);

        // act & assert
        assertThatThrownBy(() -> categoryCommandService.update(999999L, request))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("카테고리를 삭제하면 DB에서 제거된다")
    void test04() {
        // arrange - FK 없는 신규 카테고리 생성
        Category saved = categoryRepository.save(new Category("삭제대상", "#000000", "http://img.url", null));

        // act
        categoryCommandService.delete(saved.getId());

        // assert
        assertThat(categoryRepository.findById(saved.getId())).isEmpty();
    }
}
