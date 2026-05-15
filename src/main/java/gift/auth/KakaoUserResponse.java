package gift.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoUserResponse(@JsonProperty("kakao_account") KakaoAccount kakaoAccount) {

    public String email() {
        return kakaoAccount.email();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoAccount(String email) {
    }
}
