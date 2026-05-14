package gift.auth;

import gift.member.Member;
import gift.member.MemberCommandService;
import gift.member.MemberQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class KakaoAuthService {

    private final KakaoLoginClient kakaoLoginClient;
    private final MemberQueryService memberQueryService;
    private final MemberCommandService memberCommandService;
    private final JwtProvider jwtProvider;

    public KakaoAuthService(
        KakaoLoginClient kakaoLoginClient,
        MemberQueryService memberQueryService,
        MemberCommandService memberCommandService,
        JwtProvider jwtProvider
    ) {
        this.kakaoLoginClient = kakaoLoginClient;
        this.memberQueryService = memberQueryService;
        this.memberCommandService = memberCommandService;
        this.jwtProvider = jwtProvider;
    }

    public TokenResponse callback(String code) {
        KakaoTokenResponse kakaoTokenResponse = kakaoLoginClient.requestAccessToken(code);
        String accessToken = kakaoTokenResponse.accessToken();

        String email = kakaoLoginClient.requestUserInfo(accessToken).email();

        Member member = memberQueryService.findByEmailOptional(email)
            .orElseGet(() -> memberCommandService.createKakaoMember(email));

        memberCommandService.updateKakaoAccessToken(member.getId(), accessToken);

        String token = jwtProvider.createToken(member.getEmail());
        return new TokenResponse(token);
    }
}
