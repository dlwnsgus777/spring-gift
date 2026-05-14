package gift.support;

import java.util.UUID;

public class UUIDGenerator {
    public static String uuid() {
        return UUID.randomUUID().toString().substring(0, 6);
    }
}
