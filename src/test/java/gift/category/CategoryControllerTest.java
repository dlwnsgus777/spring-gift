package gift.category;

import com.fasterxml.jackson.databind.ObjectMapper;
import gift.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("전체 카테고리 목록을 조회한다")
    void test01() throws Exception {
        // arrange - Flyway V2 시드 데이터 존재

        // act & assert
        mockMvc.perform(get("/api/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("카테고리를 생성하면 201과 Location 헤더를 반환한다")
    void test02() throws Exception {
        // arrange - UUID로 다른 테스트와 이름 충돌 방지
        String uniqueName = "생성_" + UUID.randomUUID();
        CategoryRequest request = new CategoryRequest(uniqueName, "#FFFFFF", "http://img.url", null);

        // act & assert
        mockMvc.perform(post("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.name").value(uniqueName));
    }

    @Test
    @DisplayName("name이 빈 값이면 카테고리 생성에 실패한다")
    void test03() throws Exception {
        // arrange
        CategoryRequest request = new CategoryRequest("", "#FFFFFF", "http://img.url", null);

        // act & assert
        mockMvc.perform(post("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("존재하는 카테고리를 수정하면 변경된 내용을 반환한다")
    void test04() throws Exception {
        // arrange - 테스트 전용 카테고리 생성
        Category saved = categoryRepository.save(
            new Category("수정전_" + UUID.randomUUID(), "#000000", "http://img.url", null)
        );
        String updatedName = "수정후_" + UUID.randomUUID();
        CategoryRequest request = new CategoryRequest(updatedName, "#FFFFFF", "http://new.url", null);

        // act & assert
        mockMvc.perform(put("/api/categories/" + saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value(updatedName));
    }

    @Test
    @DisplayName("존재하지 않는 카테고리를 수정하면 404를 반환한다")
    void test05() throws Exception {
        // arrange
        CategoryRequest request = new CategoryRequest("이름", "#FFFFFF", "http://img.url", null);

        // act & assert
        mockMvc.perform(put("/api/categories/999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("카테고리를 삭제하면 204를 반환한다")
    void test06() throws Exception {
        // arrange - 상품 없는 신규 카테고리 생성
        Category saved = categoryRepository.save(
            new Category("삭제_" + UUID.randomUUID(), "#000000", "http://img.url", null)
        );

        // act & assert
        mockMvc.perform(delete("/api/categories/" + saved.getId()))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("존재하지 않는 카테고리를 삭제해도 204를 반환한다")
    void test07() throws Exception {
        // arrange (id does not exist)

        // act & assert
        mockMvc.perform(delete("/api/categories/999999"))
            .andExpect(status().isNoContent());
    }
}
