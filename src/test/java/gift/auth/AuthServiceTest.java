package gift.auth;

import gift.AbstractIntegrationTest;
import gift.common.UnauthorizedException;
import gift.member.Member;
import gift.member.MemberRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class AuthServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("신규 이메일로 회원가입하면 JWT 토큰을 반환한다")
    void test01() {
        // arrange
        MemberRequest request = new MemberRequest("new_" + UUID.randomUUID() + "@ex.com", "pass");

        // act
        TokenResponse result = authService.register(request);

        // assert
        assertThat(result.token()).isNotBlank();
    }

    @Test
    @DisplayName("이미 등록된 이메일로 회원가입하면 IllegalArgumentException을 던진다")
    void test02() {
        // arrange - Flyway V2 시드 데이터
        MemberRequest request = new MemberRequest("user1@example.com", "anypass");

        // act & assert
        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("등록된 이메일과 비밀번호로 로그인하면 JWT 토큰을 반환한다")
    void test03() {
        // arrange - Flyway V2 시드 데이터
        MemberRequest request = new MemberRequest("user1@example.com", "password1");

        // act
        TokenResponse result = authService.login(request);

        // assert
        assertThat(result.token()).isNotBlank();
    }

    @Test
    @DisplayName("등록되지 않은 이메일로 로그인하면 IllegalArgumentException을 던진다")
    void test04() {
        // arrange
        MemberRequest request = new MemberRequest("ghost@nowhere.com", "pass");

        // act & assert
        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 IllegalArgumentException을 던진다")
    void test05() {
        // arrange - Flyway V2 시드 데이터, 비밀번호만 틀리게
        MemberRequest request = new MemberRequest("user1@example.com", "wrongpass");

        // act & assert
        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("유효한 토큰으로 회원을 추출하면 해당 회원을 반환한다")
    void test06() {
        // arrange
        String token = jwtProvider.createToken("user1@example.com");
        String authorizationHeader = "Bearer " + token;

        // act
        Member result = authService.extractMember(authorizationHeader);

        // assert
        assertThat(result.getEmail()).isEqualTo("user1@example.com");
    }

    @Test
    @DisplayName("유효하지 않은 토큰으로 추출하면 UnauthorizedException을 던진다")
    void test07() {
        // arrange
        String authorizationHeader = "Bearer invalid";

        // act & assert
        assertThatThrownBy(() -> authService.extractMember(authorizationHeader))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("Authorization 헤더 값이 null이면 UnauthorizedException을 던진다")
    void test08() {
        // arrange
        String authorizationHeader = null;

        // act & assert
        assertThatThrownBy(() -> authService.extractMember(authorizationHeader))
            .isInstanceOf(UnauthorizedException.class);
    }
}
