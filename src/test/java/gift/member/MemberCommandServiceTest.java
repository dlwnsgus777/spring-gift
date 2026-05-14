package gift.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gift.AbstractIntegrationTest;
import gift.support.MemberFixture;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class MemberCommandServiceTest extends AbstractIntegrationTest {

    @Autowired
    private MemberCommandService memberCommandService;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("이메일과 비밀번호로 회원을 생성하고 저장된 결과를 반환한다")
    void test01() {
        // arrange
        String email = "new@example.com";
        String password = "pass1234";

        // act
        Member result = memberCommandService.create(email, password);

        // assert
        assertThat(result.getId()).isNotNull();
        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(memberRepository.findById(result.getId())).isPresent();
    }

    @Test
    @DisplayName("이미 등록된 이메일로 생성하면 IllegalArgumentException을 던진다")
    void test02() {
        // arrange - Flyway V2 시드 데이터
        String duplicateEmail = "user1@example.com";

        // act & assert
        assertThatThrownBy(() -> memberCommandService.create(duplicateEmail, "pass"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("존재하는 회원의 이메일과 비밀번호를 수정하면 변경된 내용을 반환한다")
    void test03() {
        // arrange
        Member saved = memberRepository.save(MemberFixture.builder().email("old@example.com").password("oldpass").build());

        // act
        Member result = memberCommandService.update(saved.getId(), "new@example.com", "newpass");

        // assert
        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getPassword()).isEqualTo("newpass");
    }

    @Test
    @DisplayName("존재하지 않는 ID로 수정하면 NoSuchElementException을 던진다")
    void test04() {
        // act & assert
        assertThatThrownBy(() -> memberCommandService.update(999999L, "email@ex.com", "pass"))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("회원을 삭제하면 DB에서 제거된다")
    void test05() {
        // arrange - FK 없는 신규 회원 생성
        Member saved = memberRepository.save(MemberFixture.builder().email("todelete@example.com").build());

        // act
        memberCommandService.delete(saved.getId());

        // assert
        assertThat(memberRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("회원 포인트를 충전하면 포인트가 증가한다")
    void test06() {
        // arrange
        Member saved = memberRepository.save(MemberFixture.builder().email("point@example.com").build());
        int before = saved.getPoint();

        // act
        Member result = memberCommandService.chargePoint(saved.getId(), 500);

        // assert
        assertThat(result.getPoint()).isEqualTo(before + 500);
    }

    @Test
    @DisplayName("존재하지 않는 ID로 포인트 충전하면 NoSuchElementException을 던진다")
    void test07() {
        // act & assert
        assertThatThrownBy(() -> memberCommandService.chargePoint(999999L, 100))
            .isInstanceOf(NoSuchElementException.class);
    }
}
