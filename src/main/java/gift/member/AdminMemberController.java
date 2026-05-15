package gift.member;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/members")
public class AdminMemberController {

    private final MemberQueryService memberQueryService;
    private final MemberCommandService memberCommandService;

    public AdminMemberController(
        MemberQueryService memberQueryService,
        MemberCommandService memberCommandService
    ) {
        this.memberQueryService = memberQueryService;
        this.memberCommandService = memberCommandService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("members", memberQueryService.findAll());
        return "member/list";
    }

    @GetMapping("/new")
    public String newForm() {
        return "member/new";
    }

    @PostMapping
    public String create(
        @RequestParam String email,
        @RequestParam String password,
        Model model
    ) {
        try {
            memberCommandService.create(email, password);
            return "redirect:/admin/members";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", email);
            return "member/new";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("member", memberQueryService.findById(id));
        return "member/edit";
    }

    @PostMapping("/{id}/edit")
    public String update(
        @PathVariable Long id,
        @RequestParam String email,
        @RequestParam String password
    ) {
        memberCommandService.update(id, email, password);
        return "redirect:/admin/members";
    }

    @PostMapping("/{id}/charge-point")
    public String chargePoint(
        @PathVariable Long id,
        @RequestParam int amount
    ) {
        memberCommandService.chargePoint(id, amount);
        return "redirect:/admin/members";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        memberCommandService.delete(id);
        return "redirect:/admin/members";
    }
}
