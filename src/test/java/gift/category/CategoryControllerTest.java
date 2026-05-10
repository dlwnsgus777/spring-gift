package gift.category;

import com.fasterxml.jackson.databind.ObjectMapper;
import gift.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
        categoryRepository.deleteAll();
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
    }

    @Test
    @DisplayName("전체 카테고리 목록을 조회한다")
    void test01() throws Exception {
        // arrange
        categoryRepository.save(new Category("전자기기", "#1E90FF", "http://img.url", null));

        // act & assert
        mockMvc.perform(get("/api/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].name").value("전자기기"));
    }

    @Test
    @DisplayName("카테고리를 생성하면 201과 Location 헤더를 반환한다")
    void test02() throws Exception {
        // arrange
        CategoryRequest request = new CategoryRequest("테스트", "#FFFFFF", "http://img.url", null);

        // act & assert
        mockMvc.perform(post("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.name").value("테스트"));
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
        // arrange
        Category saved = categoryRepository.save(new Category("원래이름", "#000000", "http://img.url", null));
        CategoryRequest request = new CategoryRequest("바뀐이름", "#FFFFFF", "http://new.url", null);

        // act & assert
        mockMvc.perform(put("/api/categories/" + saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("바뀐이름"));
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
        // arrange
        Category saved = categoryRepository.save(new Category("삭제대상", "#000000", "http://img.url", null));

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
