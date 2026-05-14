package gift.wish;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WishQueryService {

    private final WishRepository wishRepository;

    public WishQueryService(WishRepository wishRepository) {
        this.wishRepository = wishRepository;
    }

    public Page<Wish> findByMemberId(Long memberId, Pageable pageable) {
        return wishRepository.findByMemberId(memberId, pageable);
    }
}
