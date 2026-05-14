package gift.order;

import gift.member.Member;
import gift.member.MemberRepository;
import gift.option.Option;
import gift.option.OptionRepository;
import gift.wish.WishCommandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCommandService {

    private final OrderRepository orderRepository;
    private final OptionRepository optionRepository;
    private final MemberRepository memberRepository;
    private final WishCommandService wishCommandService;

    public OrderCommandService(
        OrderRepository orderRepository,
        OptionRepository optionRepository,
        MemberRepository memberRepository,
        WishCommandService wishCommandService
    ) {
        this.orderRepository = orderRepository;
        this.optionRepository = optionRepository;
        this.memberRepository = memberRepository;
        this.wishCommandService = wishCommandService;
    }

    @Transactional
    public Order createOrder(Long memberId, Long optionId, int quantity, String message) {
        Option option = subtractStock(optionId, quantity);
        deductPoints(memberId, option.getProduct().getPrice() * quantity);
        wishCommandService.deleteByMemberIdAndProductId(memberId, option.getProduct().getId());
        return orderRepository.save(new Order(option, memberId, quantity, message));
    }

    private Option subtractStock(Long optionId, int quantity) {
        Option option = optionRepository.findById(optionId).orElseThrow();
        option.subtractQuantity(quantity);
        return option;
    }

    private void deductPoints(Long memberId, int amount) {
        Member member = memberRepository.findById(memberId).orElseThrow();
        member.deductPoint(amount);
    }
}
