package gift.member;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class MemberQueryService {

    private final MemberRepository memberRepository;

    public MemberQueryService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public List<Member> findAll() {
        return memberRepository.findAll();
    }

    public Member findById(Long id) {
        return memberRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("회원이 존재하지 않습니다. id=" + id));
    }

    public Member findByEmail(String email) {
        return memberRepository.findByEmail(email)
            .orElseThrow(() -> new NoSuchElementException("회원이 존재하지 않습니다. email=" + email));
    }

    public Optional<Member> findByEmailOptional(String email) {
        return memberRepository.findByEmail(email);
    }
}
