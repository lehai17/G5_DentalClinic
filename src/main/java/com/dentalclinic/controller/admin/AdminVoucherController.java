package com.dentalclinic.controller.admin;

import com.dentalclinic.dto.admin.VoucherForm;
import com.dentalclinic.exception.BusinessException;
import com.dentalclinic.model.promotion.DiscountType;
import com.dentalclinic.service.admin.AdminVoucherService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/vouchers")
public class AdminVoucherController {

    private final AdminVoucherService adminVoucherService;

    public AdminVoucherController(AdminVoucherService adminVoucherService) {
        this.adminVoucherService = adminVoucherService;
    }

    @ModelAttribute("discountTypes")
    public DiscountType[] discountTypes() {
        return DiscountType.values();
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("vouchers", adminVoucherService.getAllVouchersForAdmin());
        model.addAttribute("voucherHelper", adminVoucherService);
        model.addAttribute("activePage", "vouchers");
        return "admin/voucher-list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        if (!model.containsAttribute("voucherForm")) {
            model.addAttribute("voucherForm", new VoucherForm());
        }
        model.addAttribute("assignableCustomers", adminVoucherService.getAssignableCustomers());
        model.addAttribute("activePage", "vouchers");
        return "admin/voucher-create";
    }

    @PostMapping("/create")
    public String create(@Valid @ModelAttribute("voucherForm") VoucherForm voucherForm,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("assignableCustomers", adminVoucherService.getAssignableCustomers());
            model.addAttribute("activePage", "vouchers");
            return "admin/voucher-create";
        }
        try {
            adminVoucherService.createVoucher(voucherForm);
            redirectAttributes.addFlashAttribute("success", "Tạo voucher thành công.");
            return "redirect:/admin/vouchers";
        } catch (BusinessException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("assignableCustomers", adminVoucherService.getAssignableCustomers());
            model.addAttribute("activePage", "vouchers");
            return "admin/voucher-create";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            if (!model.containsAttribute("voucherForm")) {
                model.addAttribute("voucherForm", adminVoucherService.getVoucherFormById(id));
            }
            model.addAttribute("assignableCustomers", adminVoucherService.getAssignableCustomers());
            model.addAttribute("voucherId", id);
            model.addAttribute("activePage", "vouchers");
            return "admin/voucher-edit";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/vouchers";
        }
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("voucherForm") VoucherForm voucherForm,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("assignableCustomers", adminVoucherService.getAssignableCustomers());
            model.addAttribute("voucherId", id);
            model.addAttribute("activePage", "vouchers");
            return "admin/voucher-edit";
        }
        try {
            adminVoucherService.updateVoucher(id, voucherForm);
            redirectAttributes.addFlashAttribute("success", "Cập nhật voucher thành công.");
            return "redirect:/admin/vouchers";
        } catch (BusinessException ex) {
            model.addAttribute("assignableCustomers", adminVoucherService.getAssignableCustomers());
            model.addAttribute("voucherId", id);
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("activePage", "vouchers");
            return "admin/voucher-edit";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            adminVoucherService.deleteVoucher(id);
            redirectAttributes.addFlashAttribute("success", "Đã xóa voucher.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/vouchers";
    }

    @PostMapping("/{id}/toggle-status")
    public String toggleStatus(@PathVariable Long id,
                               @RequestParam("active") boolean active,
                               RedirectAttributes redirectAttributes) {
        try {
            adminVoucherService.updateVoucherStatus(id, active);
            redirectAttributes.addFlashAttribute("success", "Cập nhật trạng thái voucher thành công.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/vouchers";
    }
}
