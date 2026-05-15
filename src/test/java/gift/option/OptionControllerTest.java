package gift.option;

import static gift.support.UUIDGenerator.uuid;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import gift.AbstractIntegrationTest;
import gift.category.CategoryRepository;
import gift.product.Product;
import gift.product.ProductRepository;
import gift.support.CategoryFixture;
import gift.support.OptionFixture;
import gift.support.ProductFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class OptionControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OptionRepository optionRepository;

    @Test
    @DisplayName("상품의 옵션 목록을 조회하면 200을 반환한다")
    void test01() throws Exception {
        // arrange
        Product product = savedProduct();
        optionRepository.save(OptionFixture.builder(product).name("옵션A_" + uuid()).build());

        // act & assert
        mockMvc.perform(get("/api/products/" + product.getId() + "/options"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("존재하지 않는 상품의 옵션을 조회하면 404를 반환한다")
    void test02() throws Exception {
        // arrange (id does not exist)

        // act & assert
        mockMvc.perform(get("/api/products/999999/options"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("유효한 요청으로 옵션을 생성하면 201과 Location 헤더를 반환한다")
    void test03() throws Exception {
        // arrange
        Product product = savedProduct();
        OptionRequest request = new OptionRequest("신규옵션_" + uuid(), 5);

        // act & assert
        mockMvc.perform(post("/api/products/" + product.getId() + "/options")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.name").value(request.name()));
    }

    @Test
    @DisplayName("같은 상품에 중복된 이름으로 옵션을 생성하면 400을 반환한다")
    void test04() throws Exception {
        // arrange
        Product product = savedProduct();
        String duplicateName = "중복옵션_" + uuid();
        optionRepository.save(OptionFixture.builder(product).name(duplicateName).build());
        OptionRequest request = new OptionRequest(duplicateName, 5);

        // act & assert
        mockMvc.perform(post("/api/products/" + product.getId() + "/options")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("허용되지 않는 특수문자가 포함된 이름으로 생성하면 400을 반환한다")
    void test05() throws Exception {
        // arrange
        Product product = savedProduct();
        OptionRequest request = new OptionRequest("옵션!@#$", 5);

        // act & assert
        mockMvc.perform(post("/api/products/" + product.getId() + "/options")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("옵션이 2개 이상인 상품에서 옵션을 삭제하면 204를 반환한다")
    void test06() throws Exception {
        // arrange
        Product product = savedProduct();
        optionRepository.save(OptionFixture.builder(product).name("옵션1_" + uuid()).build());
        Option toDelete = optionRepository.save(OptionFixture.builder(product).name("옵션2_" + uuid()).build());

        // act & assert
        mockMvc.perform(delete("/api/products/" + product.getId() + "/options/" + toDelete.getId()))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("상품의 마지막 옵션을 삭제하면 400을 반환한다")
    void test07() throws Exception {
        // arrange
        Product product = savedProduct();
        Option only = optionRepository.save(OptionFixture.builder(product).name("유일옵션_" + uuid()).build());

        // act & assert
        mockMvc.perform(delete("/api/products/" + product.getId() + "/options/" + only.getId()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("존재하지 않는 옵션을 삭제하면 404를 반환한다")
    void test08() throws Exception {
        // arrange — 옵션 2개를 만들어야 '최소 1개' 검증을 통과하고 404 경로까지 도달한다
        Product product = savedProduct();
        optionRepository.save(OptionFixture.builder(product).name("옵션A_" + uuid()).build());
        optionRepository.save(OptionFixture.builder(product).name("옵션B_" + uuid()).build());

        // act & assert
        mockMvc.perform(delete("/api/products/" + product.getId() + "/options/999999"))
            .andExpect(status().isNotFound());
    }

    private Product savedProduct() {
        var category = categoryRepository.save(CategoryFixture.builder().name("옵션테스트카테고리_" + uuid()).build());
        return productRepository.save(ProductFixture.builder(category).name("옵션테스트상품_" + uuid()).build());
    }
}
