package gift.category;

import gift.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class CategoryQueryServiceTest extends AbstractIntegrationTest {

    @Autowired
    private CategoryQueryService categoryQueryService;

    @Test
    @DisplayName("전체 카테고리 목록을 반환한다")
    void test01() {
        // arrange - Flyway V2 시드 데이터: 전자기기, 패션, 식품

        // act
        List<Category> result = categoryQueryService.findAll();

        // assert
        assertThat(result).extracting(Category::getName)
            .contains("전자기기", "패션", "식품");
    }

    @Test
    @DisplayName("존재하는 ID로 조회하면 해당 카테고리를 반환한다")
    void test02() {
        // arrange
        Category saved = categoryQueryService.findAll().get(0);

        // act
        Category result = categoryQueryService.findById(saved.getId());

        // assert
        assertThat(result.getId()).isEqualTo(saved.getId());
        assertThat(result.getName()).isEqualTo(saved.getName());
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회하면 NoSuchElementException을 던진다")
    void test03() {
        // arrange (id does not exist)

        // act & assert
        assertThatThrownBy(() -> categoryQueryService.findById(999999L))
            .isInstanceOf(NoSuchElementException.class);
    }
}
