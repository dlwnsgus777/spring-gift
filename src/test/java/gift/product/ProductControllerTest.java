package gift.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import gift.AbstractIntegrationTest;
import gift.category.Category;
import gift.category.CategoryRepository;
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

class ProductControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("상품 목록을 페이지로 조회하면 200을 반환한다")
    void test01() throws Exception {
        // arrange - Flyway V2 시드 데이터 존재

        // act & assert
        mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("존재하는 상품을 단건 조회하면 200을 반환한다")
    void test02() throws Exception {
        // arrange
        Category category = categoryRepository.save(
            new Category("단건조회_" + UUID.randomUUID(), "#FFFFFF", "http://img.com", null)
        );
        Product saved = productRepository.save(
            new Product("조회상품_" + UUID.randomUUID().toString().substring(0, 4), 1000, "http://img.com", category)
        );

        // act & assert
        mockMvc.perform(get("/api/products/" + saved.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(saved.getId()));
    }

    @Test
    @DisplayName("존재하지 않는 상품을 단건 조회하면 404를 반환한다")
    void test03() throws Exception {
        // arrange (id does not exist)

        // act & assert
        mockMvc.perform(get("/api/products/999999"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("유효한 요청으로 상품을 생성하면 201과 Location 헤더를 반환한다")
    void test04() throws Exception {
        // arrange
        Category category = categoryRepository.save(
            new Category("생성카테고리_" + UUID.randomUUID(), "#FFFFFF", "http://img.com", null)
        );
        String name = "신상품" + UUID.randomUUID().toString().substring(0, 4);
        ProductRequest request = new ProductRequest(name, 5000, "http://img.com", category.getId());

        // act & assert
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.name").value(name));
    }

    @Test
    @DisplayName("존재하지 않는 카테고리로 상품을 생성하면 404를 반환한다")
    void test05() throws Exception {
        // arrange
        ProductRequest request = new ProductRequest("상품이름", 1000, "http://img.com", 999999L);

        // act & assert
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("이름이 15자를 초과하면 400을 반환한다")
    void test06() throws Exception {
        // arrange
        Category category = categoryRepository.save(
            new Category("긴이름카테고리_" + UUID.randomUUID(), "#FFFFFF", "http://img.com", null)
        );
        ProductRequest request = new ProductRequest("a".repeat(16), 1000, "http://img.com", category.getId());

        // act & assert
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("REST API에서 카카오가 포함된 이름으로 생성하면 400을 반환한다")
    void test07() throws Exception {
        // arrange
        Category category = categoryRepository.save(
            new Category("카카오카테고리_" + UUID.randomUUID(), "#FFFFFF", "http://img.com", null)
        );
        ProductRequest request = new ProductRequest("카카오상품", 1000, "http://img.com", category.getId());

        // act & assert
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("상품을 수정하면 200과 수정된 내용을 반환한다")
    void test08() throws Exception {
        // arrange
        Category category = categoryRepository.save(
            new Category("수정카테고리_" + UUID.randomUUID(), "#FFFFFF", "http://img.com", null)
        );
        Product saved = productRepository.save(
            new Product("수정전" + UUID.randomUUID().toString().substring(0, 4), 1000, "http://img.com", category)
        );
        String updatedName = "수정후" + UUID.randomUUID().toString().substring(0, 4);
        ProductRequest request = new ProductRequest(updatedName, 2000, "http://new.com", category.getId());

        // act & assert
        mockMvc.perform(put("/api/products/" + saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value(updatedName))
            .andExpect(jsonPath("$.price").value(2000));
    }

    @Test
    @DisplayName("상품을 삭제하면 204를 반환한다")
    void test09() throws Exception {
        // arrange
        Category category = categoryRepository.save(
            new Category("삭제카테고리_" + UUID.randomUUID(), "#FFFFFF", "http://img.com", null)
        );
        Product saved = productRepository.save(
            new Product("삭제상품" + UUID.randomUUID().toString().substring(0, 4), 1000, "http://img.com", category)
        );

        // act & assert
        mockMvc.perform(delete("/api/products/" + saved.getId()))
            .andExpect(status().isNoContent());
    }
}
