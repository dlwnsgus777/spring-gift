package gift.member;

import static gift.support.UUIDGenerator.uuid;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gift.AbstractIntegrationTest;
import gift.support.MemberFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class AdminMemberControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("전체 회원 목록 페이지를 조회하면 200을 반환한다")
    void test01() throws Exception {
        // arrange - Flyway V2 시드 데이터 존재

        // act & assert
        mockMvc.perform(get("/admin/members"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("회원 생성 폼 페이지를 요청하면 200을 반환한다")
    void test02() throws Exception {
        // arrange

        // act & assert
        mockMvc.perform(get("/admin/members/new"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("유효한 이메일로 회원을 생성하면 목록 페이지로 리다이렉트한다")
    void test03() throws Exception {
        // arrange
        String email = "new_" + uuid() + "@ex.com";

        // act & assert
        mockMvc.perform(post("/admin/members")
                .param("email", email)
                .param("password", "pass"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/members"));
    }

    @Test
    @DisplayName("이미 등록된 이메일로 회원을 생성하면 200을 반환한다")
    void test04() throws Exception {
        // arrange - Flyway V2 시드 데이터의 user1 이메일 사용

        // act & assert
        mockMvc.perform(post("/admin/members")
                .param("email", "user1@example.com")
                .param("password", "pass"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("존재하는 회원의 정보를 수정하면 목록 페이지로 리다이렉트한다")
    void test05() throws Exception {
        // arrange - 테스트 전용 회원 생성
        Member member = memberRepository.save(MemberFixture.builder().email("edit_" + uuid() + "@ex.com").build());

        // act & assert
        mockMvc.perform(post("/admin/members/" + member.getId() + "/edit")
                .param("email", "edited_" + uuid() + "@ex.com")
                .param("password", "newpass"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/members"));
    }

    @Test
    @DisplayName("회원에게 포인트를 충전하면 목록 페이지로 리다이렉트한다")
    void test06() throws Exception {
        // arrange - 테스트 전용 회원 생성
        Member member = memberRepository.save(MemberFixture.builder().email("charge_" + uuid() + "@ex.com").build());

        // act & assert
        mockMvc.perform(post("/admin/members/" + member.getId() + "/charge-point")
                .param("amount", "1000"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/members"));
    }

    @Test
    @DisplayName("회원을 삭제하면 목록 페이지로 리다이렉트한다")
    void test07() throws Exception {
        // arrange - FK 없는 신규 회원 생성
        Member member = memberRepository.save(MemberFixture.builder().email("delete_" + uuid() + "@ex.com").build());

        // act & assert
        mockMvc.perform(post("/admin/members/" + member.getId() + "/delete"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/members"));
    }
}
