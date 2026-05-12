package gift.product;

import gift.category.CategoryQueryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/admin/products")
public class AdminProductController {

    private final ProductQueryService productQueryService;
    private final ProductCommandService productCommandService;
    private final CategoryQueryService categoryQueryService;

    public AdminProductController(
        ProductQueryService productQueryService,
        ProductCommandService productCommandService,
        CategoryQueryService categoryQueryService
    ) {
        this.productQueryService = productQueryService;
        this.productCommandService = productCommandService;
        this.categoryQueryService = categoryQueryService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("products", productQueryService.findAll());
        return "product/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("categories", categoryQueryService.findAll());
        return "product/new";
    }

    @PostMapping
    public String create(
        @RequestParam String name,
        @RequestParam int price,
        @RequestParam String imageUrl,
        @RequestParam Long categoryId,
        Model model
    ) {
        List<String> errors = ProductNameValidator.validate(name, true);
        if (!errors.isEmpty()) {
            populateNewForm(model, errors, name, price, imageUrl, categoryId);
            return "product/new";
        }

        productCommandService.createForAdmin(new ProductRequest(name, price, imageUrl, categoryId));
        return "redirect:/admin/products";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("product", productQueryService.findById(id));
        model.addAttribute("categories", categoryQueryService.findAll());
        return "product/edit";
    }

    @PostMapping("/{id}/edit")
    public String update(
        @PathVariable Long id,
        @RequestParam String name,
        @RequestParam int price,
        @RequestParam String imageUrl,
        @RequestParam Long categoryId,
        Model model
    ) {
        List<String> errors = ProductNameValidator.validate(name, true);
        if (!errors.isEmpty()) {
            populateEditForm(model, productQueryService.findById(id), errors, name, price, imageUrl, categoryId);
            return "product/edit";
        }

        productCommandService.updateForAdmin(id, new ProductRequest(name, price, imageUrl, categoryId));
        return "redirect:/admin/products";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        productCommandService.delete(id);
        return "redirect:/admin/products";
    }

    private void populateNewForm(
        Model model,
        List<String> errors,
        String name,
        int price,
        String imageUrl,
        Long categoryId
    ) {
        model.addAttribute("errors", errors);
        model.addAttribute("name", name);
        model.addAttribute("price", price);
        model.addAttribute("imageUrl", imageUrl);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("categories", categoryQueryService.findAll());
    }

    private void populateEditForm(
        Model model,
        Product product,
        List<String> errors,
        String name,
        int price,
        String imageUrl,
        Long categoryId
    ) {
        model.addAttribute("errors", errors);
        model.addAttribute("product", product);
        model.addAttribute("name", name);
        model.addAttribute("price", price);
        model.addAttribute("imageUrl", imageUrl);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("categories", categoryQueryService.findAll());
    }
}
