package com.dentalclinic.controller.customer;

import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.model.service.Services;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.repository.ServiceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/customer")
public class CustomerDetailController {

    private final ServiceRepository serviceRepo;
    private final DentistProfileRepository dentistRepo;

    public CustomerDetailController(ServiceRepository serviceRepo, DentistProfileRepository dentistRepo) {
        this.serviceRepo = serviceRepo;
        this.dentistRepo = dentistRepo;
    }

    @GetMapping("/services/{id}")
    public String serviceDetail(@PathVariable("id") Long id, Model model) {
        Services service = serviceRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));

        if (!service.isActive()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Service is not active");
        }

        model.addAttribute("service", service);
        model.addAttribute("active", "homepage");
        return "customer/service-detail";
    }

    @GetMapping("/dentists/{id}")
    public String dentistDetail(@PathVariable("id") Long id, Model model) {
        DentistProfile dentist = dentistRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dentist not found"));

        model.addAttribute("dentist", dentist);
        model.addAttribute("active", "homepage");
        return "customer/dentist-detail";
    }
}
