package gift.order;

import gift.auth.AuthService;
import gift.member.Member;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final AuthService authService;
    private final OrderQueryService orderQueryService;
    private final OrderCommandService orderCommandService;
    private final NotifySendService notifySendService;

    public OrderController(
        AuthService authService,
        OrderQueryService orderQueryService,
        OrderCommandService orderCommandService,
        NotifySendService notifySendService
    ) {
        this.authService = authService;
        this.orderQueryService = orderQueryService;
        this.orderCommandService = orderCommandService;
        this.notifySendService = notifySendService;
    }

    @GetMapping
    public ResponseEntity<Page<OrderResponse>> getOrders(
        @RequestHeader("Authorization") String authorization,
        Pageable pageable
    ) {
        Member member = authService.extractMember(authorization);
        Page<OrderResponse> orders = orderQueryService.findByMemberId(member.getId(), pageable).map(OrderResponse::from);
        return ResponseEntity.ok(orders);
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody OrderRequest request
    ) {
        Member member = authService.extractMember(authorization);
        Order order = orderCommandService.createOrder(member.getId(), request.optionId(), request.quantity(), request.message());
        notifySendService.sendIfPossible(member, order);
        return ResponseEntity.created(URI.create("/api/orders/" + order.getId()))
            .body(OrderResponse.from(order));
    }
}
