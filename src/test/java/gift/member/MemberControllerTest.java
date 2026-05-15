package gift.member;

import static gift.support.UUIDGenerator.uuid;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import gift.AbstractIntegrationTest;
import gift.support.MemberFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class MemberControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("유효한 이메일과 비밀번호로 회원가입하면 201과 JWT 토큰을 반환한다")
    void test01() throws Exception {
        // arrange
        MemberRequest request = new MemberRequest("new_" + uuid() + "@ex.com", "pass");

        // act & assert
        mockMvc.perform(post("/api/members/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("이미 등록된 이메일로 회원가입하면 400을 반환한다")
    void test02() throws Exception {
        // arrange
        String email = "dup_" + uuid() + "@ex.com";
        memberRepository.save(MemberFixture.builder().email(email).build());
        MemberRequest request = new MemberRequest(email, "anotherpass");

        // act & assert
        mockMvc.perform(post("/api/members/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이메일 형식이 아닌 값으로 회원가입하면 400을 반환한다")
    void test03() throws Exception {
        // arrange
        MemberRequest request = new MemberRequest("not-an-email", "pass");

        // act & assert
        mockMvc.perform(post("/api/members/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("등록된 이메일과 비밀번호로 로그인하면 200과 JWT 토큰을 반환한다")
    void test04() throws Exception {
        // arrange - Flyway V2 시드 데이터 사용
        MemberRequest request = new MemberRequest("user1@example.com", "password1");

        // act & assert
        mockMvc.perform(post("/api/members/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("등록되지 않은 이메일로 로그인하면 400을 반환한다")
    void test05() throws Exception {
        // arrange
        MemberRequest request = new MemberRequest("ghost_" + uuid() + "@ex.com", "pass");

        // act & assert
        mockMvc.perform(post("/api/members/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 400을 반환한다")
    void test06() throws Exception {
        // arrange - Flyway V2 시드 데이터 사용, 비밀번호만 틀리게
        MemberRequest request = new MemberRequest("user1@example.com", "wrongpass");

        // act & assert
        mockMvc.perform(post("/api/members/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }
}
