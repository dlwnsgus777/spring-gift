package gift.auth;

public class FakeKakaoLoginClient extends KakaoLoginClient {

    private String fixedAccessToken = "fake-access-token";
    private String fixedEmail = "fake@example.com";

    public FakeKakaoLoginClient() {
        super();
    }

    @Override
    public KakaoTokenResponse requestAccessToken(String code) {
        return new KakaoTokenResponse(fixedAccessToken);
    }

    @Override
    public KakaoUserResponse requestUserInfo(String accessToken) {
        return new KakaoUserResponse(new KakaoUserResponse.KakaoAccount(fixedEmail));
    }

    public void setFixedEmail(String email) {
        this.fixedEmail = email;
    }
}
