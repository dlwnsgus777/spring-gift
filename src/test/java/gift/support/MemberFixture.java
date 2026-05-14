package gift.support;

import gift.member.Member;

public class MemberFixture {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String email = "member_" + UUIDGenerator.uuid() + "@test.com";
        private String password = "password";

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Member build() {
            return new Member(email, password);
        }
    }
}
