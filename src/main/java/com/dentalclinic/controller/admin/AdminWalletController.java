package com.dentalclinic.controller.admin;

import com.dentalclinic.service.admin.AdminWalletService;
import com.dentalclinic.dto.admin.AdminWalletTransactionRowDTO;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/admin/wallets")
public class AdminWalletController {

    private final AdminWalletService adminWalletService;

    public AdminWalletController(AdminWalletService adminWalletService) {
        this.adminWalletService = adminWalletService;
    }

    @GetMapping("/transactions")
    public String showTransactions(@RequestParam(value = "keyword", required = false) String keyword,
                                   @RequestParam(value = "type", required = false) String type,
                                   @RequestParam(value = "dateFrom", required = false) LocalDate dateFrom,
                                   @RequestParam(value = "dateTo", required = false) LocalDate dateTo,
                                   @RequestParam(value = "page", defaultValue = "1") int page,
                                   Model model) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            LocalDate temp = dateFrom;
            dateFrom = dateTo;
            dateTo = temp;
        }

        int pageSize = 5;
        int currentPage = Math.max(page, 1);
        List<AdminWalletTransactionRowDTO> filteredTransactions = adminWalletService.getTransactionRows(keyword, type, dateFrom, dateTo);
        int totalItems = filteredTransactions.size();
        int totalPages = Math.max((int) Math.ceil(totalItems / (double) pageSize), 1);
        if (currentPage > totalPages) {
            currentPage = totalPages;
        }
        int fromIndex = Math.min((currentPage - 1) * pageSize, totalItems);
        int toIndex = Math.min(fromIndex + pageSize, totalItems);

        model.addAttribute("overview", adminWalletService.getOverview());
        model.addAttribute("transactions", filteredTransactions.subList(fromIndex, toIndex));
        model.addAttribute("types", adminWalletService.getSupportedTransactionTypes());
        model.addAttribute("typeLabels", adminWalletService.getSupportedTransactionTypeLabels());
        model.addAttribute("selectedKeyword", keyword);
        model.addAttribute("selectedType", type);
        model.addAttribute("selectedDateFrom", dateFrom);
        model.addAttribute("selectedDateTo", dateTo);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageNumbers", java.util.stream.IntStream.rangeClosed(1, totalPages).boxed().toList());
        model.addAttribute("filteredCount", totalItems);
        model.addAttribute("activePage", "wallet-transactions");
        return "admin/wallet-transaction-list";
    }
}
