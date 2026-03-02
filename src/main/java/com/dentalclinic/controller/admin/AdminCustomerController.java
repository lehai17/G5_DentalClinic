package com.dentalclinic.controller.admin;

import com.dentalclinic.dto.admin.CustomerListDTO;
import com.dentalclinic.dto.admin.CustomerDetailDTO;
import com.dentalclinic.model.user.UserStatus;
import com.dentalclinic.service.admin.AdminCustomerService;
import com.dentalclinic.service.admin.AdminServiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/admin/customers")
public class AdminCustomerController {

    @Autowired
    private AdminCustomerService adminCustomerService;

    @Autowired
    private AdminServiceService adminServiceService;

    @GetMapping
    public String showCustomerList(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String statusStr,
            @RequestParam(value = "serviceId", required = false) Long serviceId,
            Model model) {

        UserStatus status = null;
        if (statusStr != null && !statusStr.isEmpty()) {
            try {
                status = UserStatus.valueOf(statusStr);
            } catch (IllegalArgumentException e) {
                // Ignore invalid status
            }
        }

        List<CustomerListDTO> customers = adminCustomerService.searchCustomers(keyword, status, serviceId);

        model.addAttribute("customers", customers);
        model.addAttribute("stats", adminCustomerService.getCustomerStats());
        model.addAttribute("services", adminServiceService.getAllServices()); // To populate service filter dropdown

        model.addAttribute("selectedKeyword", keyword);
        model.addAttribute("selectedStatus", statusStr);
        model.addAttribute("selectedServiceId", serviceId);
        model.addAttribute("activePage", "customers");

        return "admin/customer-list";
    }

    @GetMapping("/api/search")
    public String searchCustomersApi(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String statusStr,
            @RequestParam(value = "serviceId", required = false) Long serviceId,
            Model model) {

        UserStatus status = null;
        if (statusStr != null && !statusStr.isEmpty()) {
            try {
                status = UserStatus.valueOf(statusStr);
            } catch (IllegalArgumentException e) {
                // Ignore invalid status
            }
        }

        List<CustomerListDTO> customers = adminCustomerService.searchCustomers(keyword, status, serviceId);
        model.addAttribute("customers", customers);

        return "admin/fragments/customer-table :: customer-list";
    }

    @GetMapping("/detail/{id}")
    public String showCustomerDetail(@PathVariable("id") Long id, Model model) {
        CustomerDetailDTO customer = adminCustomerService.getCustomerDetail(id);
        model.addAttribute("customer", customer);
        model.addAttribute("history", adminCustomerService.getCustomerHistory(id));
        model.addAttribute("upcoming", adminCustomerService.getUpcomingAppointments(id));
        model.addAttribute("activePage", "customers");

        return "admin/customer-detail";
    }
}
