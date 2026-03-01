package com.dentalclinic.controller.dentist;

import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.service.dentist.DentistProfileService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/dentist/profile")
public class DentistProfileController {

    private final DentistProfileService profileService;

    public DentistProfileController(DentistProfileService profileService) {
        this.profileService = profileService;
    }

    // ================= VIEW =================
    @GetMapping
    public String viewProfile(Authentication authentication, Model model) {

        String email = authentication.getName();

        DentistProfile profile = profileService.getProfileByEmail(email);

        model.addAttribute("profile", profile);
        model.addAttribute("user", profile.getUser());
        model.addAttribute("age",
                profileService.calculateAge(profile.getUser()));

        return "dentist/profile";
    }

    // ================= EDIT FORM =================
    @GetMapping("/edit")
    public String editProfile(Authentication authentication, Model model) {
        String email = authentication.getName();
        DentistProfileEditDTO dto = profileService.getEditDTO(email);
        model.addAttribute("editDTO", dto);
        return "dentist/profile-edit";
    }

    // ================= SAVE =================
    @PostMapping("/edit")
    public String saveProfile(Authentication authentication,
                              @ModelAttribute DentistProfileEditDTO dto) {

        String email = authentication.getName();

        profileService.updateProfile(email, dto);

        return "redirect:/dentist/profile";
    }
}