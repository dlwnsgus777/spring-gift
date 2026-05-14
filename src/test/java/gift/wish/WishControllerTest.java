package gift.wish;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import gift.AbstractIntegrationTest;
import gift.auth.JwtProvider;
import gift.category.Category;
import gift.category.CategoryRepository;
import gift.product.Product;
import gift.product.ProductRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class WishControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WishRepository wishRepository;

    @Test
    @DisplayName("인증된 회원의 위시리스트를 조회하면 200을 반환한다")
    void test01() throws Exception {
        // arrange
        String token = "Bearer " + jwtProvider.createToken("user1@example.com");

        // act & assert
        mockMvc.perform(get("/api/wishes")
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("토큰 없이 위시리스트를 조회하면 400을 반환한다")
    void test02() throws Exception {
        // arrange — Authorization 헤더를 포함하지 않음
        // WishController가 @RequestHeader("Authorization")를 사용하므로
        // Spring이 MissingRequestHeaderException(400)을 던진다

        // act & assert
        mockMvc.perform(get("/api/wishes"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("유효한 상품 ID로 위시를 추가하면 201을 반환한다")
    void test03() throws Exception {
        // arrange
        String token = "Bearer " + jwtProvider.createToken("user1@example.com");
        Product product = savedProduct();
        WishRequest request = new WishRequest(product.getId());

        // act & assert
        mockMvc.perform(post("/api/wishes")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.productId").value(product.getId()));
    }

    @Test
    @DisplayName("이미 추가된 상품을 다시 추가하면 200을 반환한다")
    void test04() throws Exception {
        // arrange
        String token = "Bearer " + jwtProvider.createToken("user1@example.com");
        // user1의 시드 위시: product_id=1 (맥북 프로 16인치)
        WishRequest request = new WishRequest(1L);

        // act & assert
        mockMvc.perform(post("/api/wishes")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(1L));
    }

    @Test
    @DisplayName("존재하지 않는 상품 ID로 위시를 추가하면 404를 반환한다")
    void test05() throws Exception {
        // arrange
        String token = "Bearer " + jwtProvider.createToken("user1@example.com");
        WishRequest request = new WishRequest(999999L);

        // act & assert
        mockMvc.perform(post("/api/wishes")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("본인 위시를 삭제하면 204를 반환한다")
    void test06() throws Exception {
        // arrange
        String token = "Bearer " + jwtProvider.createToken("user1@example.com");
        Product product = savedProduct();
        // user1의 memberId=2
        Wish wish = wishRepository.save(new Wish(2L, product));

        // act & assert
        mockMvc.perform(delete("/api/wishes/" + wish.getId())
                .header("Authorization", token))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("다른 회원의 위시를 삭제하려 하면 403을 반환한다")
    void test07() throws Exception {
        // arrange
        // user1 소유 위시에 user2 토큰으로 접근
        String user2Token = "Bearer " + jwtProvider.createToken("user2@example.com");
        Product product = savedProduct();
        // user1의 memberId=2
        Wish wish = wishRepository.save(new Wish(2L, product));

        // act & assert
        mockMvc.perform(delete("/api/wishes/" + wish.getId())
                .header("Authorization", user2Token))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("존재하지 않는 위시를 삭제하면 404를 반환한다")
    void test08() throws Exception {
        // arrange
        String token = "Bearer " + jwtProvider.createToken("user1@example.com");

        // act & assert
        mockMvc.perform(delete("/api/wishes/999999")
                .header("Authorization", token))
            .andExpect(status().isNotFound());
    }

    private Product savedProduct() {
        Category category = categoryRepository.save(
            new Category("위시테스트카테고리_" + uuid(), "#FFFFFF", "http://img.com", null)
        );
        return productRepository.save(
            new Product("위시테스트상품_" + uuid(), 1000, "http://img.com", category)
        );
    }

    private String uuid() {
        return UUID.randomUUID().toString().substring(0, 6);
    }
}
