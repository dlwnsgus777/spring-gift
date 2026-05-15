package gift.auth;

import gift.common.UnauthorizedException;
import gift.member.Member;
import gift.member.MemberCommandService;
import gift.member.MemberQueryService;
import gift.member.MemberRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@Transactional
public class AuthService {

    private final MemberCommandService memberCommandService;
    private final MemberQueryService memberQueryService;
    private final JwtProvider jwtProvider;

    public AuthService(
        MemberCommandService memberCommandService,
        MemberQueryService memberQueryService,
        JwtProvider jwtProvider
    ) {
        this.memberCommandService = memberCommandService;
        this.memberQueryService = memberQueryService;
        this.jwtProvider = jwtProvider;
    }

    public TokenResponse register(MemberRequest request) {
        Member member = memberCommandService.create(request.email(), request.password());
        return new TokenResponse(jwtProvider.createToken(member.getEmail()));
    }

    @Transactional(readOnly = true)
    public Member extractMember(String authorization) {
        try {
            String token = authorization.replace("Bearer ", "");
            String email = jwtProvider.getEmail(token);
            return memberQueryService.findByEmail(email);
        } catch (Exception e) {
            throw new UnauthorizedException("Invalid or missing token.");
        }
    }

    public TokenResponse login(MemberRequest request) {
        Member member;
        try {
            member = memberQueryService.findByEmail(request.email());
        } catch (NoSuchElementException e) {
            throw new IllegalArgumentException("Invalid email or password.");
        }

        if (member.getPassword() == null || !member.getPassword().equals(request.password())) {
            throw new IllegalArgumentException("Invalid email or password.");
        }

        return new TokenResponse(jwtProvider.createToken(member.getEmail()));
    }
}
