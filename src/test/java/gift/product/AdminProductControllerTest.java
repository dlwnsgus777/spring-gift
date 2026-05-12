package gift.product;

import gift.AbstractIntegrationTest;
import gift.category.Category;
import gift.category.CategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminProductControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("상품 목록 페이지를 요청하면 200을 반환한다")
    void test01() throws Exception {
        // arrange - Flyway V2 시드 데이터 존재

        // act & assert
        mockMvc.perform(get("/admin/products"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("상품 등록 폼 페이지를 요청하면 200을 반환한다")
    void test02() throws Exception {
        // arrange

        // act & assert
        mockMvc.perform(get("/admin/products/new"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("유효한 데이터로 상품을 등록하면 목록 페이지로 리다이렉트한다")
    void test03() throws Exception {
        // arrange
        Category category = categoryRepository.save(
            new Category("Admin생성_" + UUID.randomUUID(), "#FFFFFF", "http://img.com", null)
        );
        String name = "Admin상품" + UUID.randomUUID().toString().substring(0, 4);

        // act & assert
        mockMvc.perform(post("/admin/products")
                .param("name", name)
                .param("price", "5000")
                .param("imageUrl", "http://img.com")
                .param("categoryId", String.valueOf(category.getId())))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/products"));
    }

    @Test
    @DisplayName("Admin에서 카카오가 포함된 상품명으로 등록하면 목록 페이지로 리다이렉트한다")
    void test04() throws Exception {
        // arrange - Admin은 카카오 포함 이름 허용
        Category category = categoryRepository.save(
            new Category("Admin카카오_" + UUID.randomUUID(), "#FFFFFF", "http://img.com", null)
        );

        // act & assert
        mockMvc.perform(post("/admin/products")
                .param("name", "카카오선물")
                .param("price", "1000")
                .param("imageUrl", "http://img.com")
                .param("categoryId", String.valueOf(category.getId())))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/products"));
    }

    @Test
    @DisplayName("존재하는 상품의 수정 폼 페이지를 요청하면 200을 반환한다")
    void test05() throws Exception {
        // arrange
        Category category = categoryRepository.save(
            new Category("수정폼_" + UUID.randomUUID(), "#FFFFFF", "http://img.com", null)
        );
        Product saved = productRepository.save(
            new Product("수정전" + UUID.randomUUID().toString().substring(0, 4), 1000, "http://img.com", category)
        );

        // act & assert
        mockMvc.perform(get("/admin/products/" + saved.getId() + "/edit"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("상품 수정을 완료하면 목록 페이지로 리다이렉트한다")
    void test06() throws Exception {
        // arrange
        Category category = categoryRepository.save(
            new Category("수정완료_" + UUID.randomUUID(), "#FFFFFF", "http://img.com", null)
        );
        Product saved = productRepository.save(
            new Product("수정전" + UUID.randomUUID().toString().substring(0, 4), 1000, "http://img.com", category)
        );

        // act & assert
        mockMvc.perform(post("/admin/products/" + saved.getId() + "/edit")
                .param("name", "수정후" + UUID.randomUUID().toString().substring(0, 4))
                .param("price", "9000")
                .param("imageUrl", "http://new.com")
                .param("categoryId", String.valueOf(category.getId())))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/products"));
    }

    @Test
    @DisplayName("상품을 삭제하면 목록 페이지로 리다이렉트한다")
    void test07() throws Exception {
        // arrange
        Category category = categoryRepository.save(
            new Category("삭제Admin_" + UUID.randomUUID(), "#FFFFFF", "http://img.com", null)
        );
        Product saved = productRepository.save(
            new Product("삭제상품" + UUID.randomUUID().toString().substring(0, 4), 1000, "http://img.com", category)
        );

        // act & assert
        mockMvc.perform(post("/admin/products/" + saved.getId() + "/delete"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/products"));
    }
}
