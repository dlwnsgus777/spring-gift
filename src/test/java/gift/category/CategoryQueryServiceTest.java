package gift.category;

import gift.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}
