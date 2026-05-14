package gift.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import gift.AbstractIntegrationTest;
import gift.auth.JwtProvider;
import gift.category.Category;
import gift.category.CategoryRepository;
import gift.member.Member;
import gift.member.MemberRepository;
import gift.option.Option;
import gift.option.OptionRepository;
import gift.product.Product;
import gift.product.ProductRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class OrderControllerTest extends AbstractIntegrationTest {

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
    private OptionRepository optionRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("포인트와 재고가 충분하면 주문이 생성되고 201을 반환한다")
    void test01() throws Exception {
        // arrange
        String email = "order-test-" + uuid() + "@example.com";
        Option option = savedOption(1000, 10);

        Member member = new Member(email, "password");
        member.chargePoint(1000); // price(1000) * quantity(1) = 1000
        memberRepository.save(member);

        String token = bearerToken(email);
        OrderRequest request = new OrderRequest(option.getId(), 1, "테스트");

        // act & assert
        mockMvc.perform(post("/api/orders")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.optionId").value(option.getId()))
            .andExpect(jsonPath("$.quantity").value(1));
    }

    @Test
    @DisplayName("Authorization 헤더 없이 주문하면 400을 반환한다")
    void test02() throws Exception {
        // arrange
        OrderRequest request = new OrderRequest(1L, 1, "테스트");

        // act & assert
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("유효하지 않은 토큰으로 주문하면 401을 반환한다")
    void test03() throws Exception {
        // arrange
        OrderRequest request = new OrderRequest(1L, 1, "테스트");

        // act & assert
        mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer invalid_token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("존재하지 않는 optionId로 주문하면 404를 반환한다")
    void test04() throws Exception {
        // arrange
        String email = "order-test04-" + uuid() + "@example.com";

        Member member = new Member(email, "password");
        member.chargePoint(100000);
        memberRepository.save(member);

        String token = bearerToken(email);
        OrderRequest request = new OrderRequest(999999999L, 1, "테스트");

        // act & assert
        mockMvc.perform(post("/api/orders")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("재고보다 많은 수량으로 주문하면 400을 반환한다")
    void test05() throws Exception {
        // arrange
        String email = "order-test05-" + uuid() + "@example.com";
        Option option = savedOption(100, 2); // 재고 2

        Member member = new Member(email, "password");
        member.chargePoint(1000000);
        memberRepository.save(member);

        String token = bearerToken(email);
        OrderRequest request = new OrderRequest(option.getId(), 10, "테스트"); // 재고(2)보다 많은 수량(10)

        // act & assert
        mockMvc.perform(post("/api/orders")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("포인트가 부족하면 400을 반환한다")
    void test06() throws Exception {
        // arrange
        String email = "order-test06-" + uuid() + "@example.com";
        Option option = savedOption(100000, 100); // 가격 100000

        Member member = new Member(email, "password"); // 포인트 0 — chargePoint 미호출
        memberRepository.save(member);

        String token = bearerToken(email);
        OrderRequest request = new OrderRequest(option.getId(), 1, "테스트"); // 가격 100000 > 포인트 0

        // act & assert
        mockMvc.perform(post("/api/orders")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인증된 회원의 주문 목록을 조회하면 200을 반환한다")
    void test07() throws Exception {
        // arrange
        String email = "order-test07-" + uuid() + "@example.com";
        memberRepository.save(new Member(email, "password"));

        String token = bearerToken(email);

        // act & assert
        mockMvc.perform(get("/api/orders")
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    private Option savedOption(int price, int stock) {
        String uid = uuid();
        Category category = categoryRepository.save(
            new Category("주문테스트카테고리_" + uid, "#FFFFFF", "http://img.com", null)
        );
        Product product = productRepository.save(
            new Product("주문테스트상품_" + uid, price, "http://img.com", category)
        );
        return optionRepository.save(
            new Option(product, "주문테스트옵션_" + uid, stock)
        );
    }

    private String bearerToken(String email) {
        return "Bearer " + jwtProvider.createToken(email);
    }

    private String uuid() {
        return UUID.randomUUID().toString().substring(0, 6);
    }
}
