package gift.option;

import gift.product.ProductCommandService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping(path = "/api/products/{productId}/options")
public class OptionController {
    private final OptionQueryService optionQueryService;
    private final ProductCommandService productCommandService;

    public OptionController(OptionQueryService optionQueryService, ProductCommandService productCommandService) {
        this.optionQueryService = optionQueryService;
        this.productCommandService = productCommandService;
    }

    @GetMapping
    public ResponseEntity<List<OptionResponse>> getOptions(@PathVariable Long productId) {
        List<OptionResponse> options = optionQueryService.findByProductId(productId).stream()
            .map(OptionResponse::from)
            .toList();
        return ResponseEntity.ok(options);
    }

    @PostMapping
    public ResponseEntity<OptionResponse> createOption(
        @PathVariable Long productId,
        @Valid @RequestBody OptionRequest request
    ) {
        Option saved = productCommandService.addOption(productId, request);
        URI location = URI.create("/api/products/" + productId + "/options/" + saved.getId());
        return ResponseEntity.created(location).body(OptionResponse.from(saved));
    }

    @DeleteMapping("/{optionId}")
    public ResponseEntity<Void> deleteOption(
        @PathVariable Long productId,
        @PathVariable Long optionId
    ) {
        productCommandService.removeOption(productId, optionId);
        return ResponseEntity.noContent().build();
    }
}
