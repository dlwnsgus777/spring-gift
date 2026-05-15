package gift.wish;

import gift.auth.AuthService;
import gift.member.Member;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/wishes")
public class WishController {
    private final AuthService authService;
    private final WishQueryService wishQueryService;
    private final WishCommandService wishCommandService;

    public WishController(
        AuthService authService,
        WishQueryService wishQueryService,
        WishCommandService wishCommandService
    ) {
        this.authService = authService;
        this.wishQueryService = wishQueryService;
        this.wishCommandService = wishCommandService;
    }

    @GetMapping
    public ResponseEntity<Page<WishResponse>> getWishes(
        @RequestHeader("Authorization") String authorization,
        Pageable pageable
    ) {
        Member member = authService.extractMember(authorization);
        Page<WishResponse> wishes = wishQueryService.findByMemberId(member.getId(), pageable)
            .map(WishResponse::from);
        return ResponseEntity.ok(wishes);
    }

    @PostMapping
    public ResponseEntity<WishResponse> addWish(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody WishRequest request
    ) {
        Member member = authService.extractMember(authorization);
        WishResult result = wishCommandService.addWish(member.getId(), request.productId());
        if (!result.isNew()) {
            return ResponseEntity.ok(WishResponse.from(result.wish()));
        }
        return ResponseEntity.created(URI.create("/api/wishes/" + result.wish().getId()))
            .body(WishResponse.from(result.wish()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeWish(
        @RequestHeader("Authorization") String authorization,
        @PathVariable Long id
    ) {
        Member member = authService.extractMember(authorization);
        wishCommandService.removeWish(member.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
