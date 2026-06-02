package gift.member;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@Transactional
public class MemberCommandService {

    private final MemberRepository memberRepository;

    public MemberCommandService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public Member create(String email, String password) {
        if (memberRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered.");
        }
        return memberRepository.save(new Member(email, password));
    }

    public Member update(Long id, String email, String password) {
        Member member = memberRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("회원이 존재하지 않습니다. id=" + id));
        member.update(email, password);
        return member;
    }

    public void delete(Long id) {
        memberRepository.deleteById(id);
    }

    public Member chargePoint(Long id, int amount) {
        Member member = memberRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("회원이 존재하지 않습니다. id=" + id));
        member.chargePoint(amount);
        return member;
    }

    public Member createKakaoMember(String email) {
        return memberRepository.save(new Member(email));
    }

    public Member updateKakaoAccessToken(Long id, String kakaoAccessToken) {
        Member member = memberRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("회원이 존재하지 않습니다. id=" + id));
        member.updateKakaoAccessToken(kakaoAccessToken);
        return member;
    }
}
