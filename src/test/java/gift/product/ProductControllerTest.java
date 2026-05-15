package gift.product;

import static gift.support.UUIDGenerator.uuid;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import gift.AbstractIntegrationTest;
import gift.category.CategoryRepository;
import gift.support.CategoryFixture;
import gift.support.ProductFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
        var category = categoryRepository.save(CategoryFixture.builder().name("단건조회_" + uuid()).build());
        Product saved = productRepository.save(ProductFixture.builder(category).name("조회상품_" + uuid()).build());

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
        var category = categoryRepository.save(CategoryFixture.builder().name("생성카테고리_" + uuid()).build());
        String name = "신상품" + uuid();
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
        var category = categoryRepository.save(CategoryFixture.builder().name("긴이름카테고리_" + uuid()).build());
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
        var category = categoryRepository.save(CategoryFixture.builder().name("카카오카테고리_" + uuid()).build());
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
        var category = categoryRepository.save(CategoryFixture.builder().name("수정카테고리_" + uuid()).build());
        Product saved = productRepository.save(ProductFixture.builder(category).name("수정전" + uuid()).build());
        String updatedName = "수정후" + uuid();
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
        var category = categoryRepository.save(CategoryFixture.builder().name("삭제카테고리_" + uuid()).build());
        Product saved = productRepository.save(ProductFixture.builder(category).name("삭제상품" + uuid()).build());

        // act & assert
        mockMvc.perform(delete("/api/products/" + saved.getId()))
            .andExpect(status().isNoContent());
    }
}
