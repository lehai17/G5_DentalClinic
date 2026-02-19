package com.dentalclinic.controller.staff;

import com.dentalclinic.model.blog.Blog;
import com.dentalclinic.model.blog.BlogStatus;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.BlogRepository;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.BlogWorkflowService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/staff/blogs")
public class StaffBlogController {

    private final BlogRepository blogRepository;
    private final UserRepository userRepository;
    private final BlogWorkflowService workflowService;

    public StaffBlogController(BlogRepository blogRepository,
                               UserRepository userRepository,
                               BlogWorkflowService workflowService) {
        this.blogRepository = blogRepository;
        this.userRepository = userRepository;
        this.workflowService = workflowService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) BlogStatus status,
                       @RequestParam(defaultValue = "0") int page,
                       Authentication authentication,
                       Model model) {
        User staff = userRepository.findByEmail(authentication.getName()).orElseThrow();
        Pageable pageable = PageRequest.of(page, 10);

        if (status == null) {
            model.addAttribute("blogPage", blogRepository.findByCreatedByOrderByUpdatedAtDesc(staff, pageable));
        } else {
            model.addAttribute("blogPage", blogRepository.findByCreatedByAndStatusOrderByUpdatedAtDesc(staff, status, pageable));
        }

        model.addAttribute("status", status);
        model.addAttribute("activePage", "blogs");
        return "staff/blog-list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("blog", new Blog());
        model.addAttribute("activePage", "blogs");
        return "staff/blog-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Authentication authentication, Model model) {
        User staff = userRepository.findByEmail(authentication.getName()).orElseThrow();
        Blog blog = blogRepository.findById(id).orElseThrow();

        if (!blog.getCreatedBy().getId().equals(staff.getId())) {
            return "redirect:/staff/blogs?forbidden=true";
        }

        model.addAttribute("blog", blog);
        model.addAttribute("activePage", "blogs");
        return "staff/blog-form";
    }

    /**
     * Save Draft: dùng cho cả create mới và update bản draft/rejected
     */
    @PostMapping("/save-draft")
    public String saveDraft(@ModelAttribute Blog form,
                            @RequestParam(value = "imageFile", required = false) org.springframework.web.multipart.MultipartFile imageFile,
                            @RequestParam(value = "existingImageUrl", required = false) String existingImageUrl,
                            Authentication authentication) throws java.io.IOException {

        User staff = userRepository.findByEmail(authentication.getName()).orElseThrow();

        // set imageUrl
        if (imageFile != null && !imageFile.isEmpty()) {
            form.setImageUrl(storeBlogImage(imageFile));
        } else {
            form.setImageUrl(existingImageUrl); // giữ ảnh cũ nếu không upload mới
        }

        if (form.getId() == null) {
            workflowService.createDraft(form, staff);
        } else {
            Blog existing = blogRepository.findById(form.getId()).orElseThrow();
            // chặn sửa blog người khác
            if (!existing.getCreatedBy().getId().equals(staff.getId())) {
                return "redirect:/staff/blogs?forbidden=true";
            }
            workflowService.updateByStaff(existing, form, staff);
        }

        return "redirect:/staff/blogs?saved=true";
    }


    /**
     * Submit Review: dùng cho cả create mới và edit rồi submit
     */
    @PostMapping("/submit-review")
    public String submitReview(@ModelAttribute Blog form,
                               @RequestParam(value = "imageFile", required = false) org.springframework.web.multipart.MultipartFile imageFile,
                               @RequestParam(value = "existingImageUrl", required = false) String existingImageUrl,
                               Authentication authentication) throws java.io.IOException {

        User staff = userRepository.findByEmail(authentication.getName()).orElseThrow();

        if (imageFile != null && !imageFile.isEmpty()) {
            form.setImageUrl(storeBlogImage(imageFile));
        } else {
            form.setImageUrl(existingImageUrl);
        }

        Blog target;
        if (form.getId() == null) {
            target = workflowService.createDraft(form, staff);
        } else {
            Blog existing = blogRepository.findById(form.getId()).orElseThrow();
            if (!existing.getCreatedBy().getId().equals(staff.getId())) {
                return "redirect:/staff/blogs?forbidden=true";
            }
            target = workflowService.updateByStaff(existing, form, staff);
        }

        workflowService.submitForReview(target, staff);
        return "redirect:/staff/blogs?submitted=true";
    }


    private void assertOwner(Blog blog, User staff) {
        if (!blog.getCreatedBy().getId().equals(staff.getId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN
            );
        }
    }

    @PostMapping("/{id}/submit")
    public String submit(@PathVariable Long id, Authentication authentication) {
        User staff = userRepository.findByEmail(authentication.getName()).orElseThrow();
        Blog blog = blogRepository.findById(id).orElseThrow();
        assertOwner(blog, staff);
        workflowService.submitForReview(blog, staff);
        return "redirect:/staff/blogs?submitted=true";
    }

    @PostMapping("/{id}/withdraw")
    public String withdraw(@PathVariable Long id, Authentication authentication) {
        User staff = userRepository.findByEmail(authentication.getName()).orElseThrow();
        Blog blog = blogRepository.findById(id).orElseThrow();
        assertOwner(blog, staff);
        workflowService.withdrawPending(blog, staff);
        return "redirect:/staff/blogs?withdrawn=true";
    }
    private String storeBlogImage(org.springframework.web.multipart.MultipartFile imageFile) throws java.io.IOException {
        String ext = org.springframework.util.StringUtils.getFilenameExtension(imageFile.getOriginalFilename());
        String fileName = java.util.UUID.randomUUID() + (ext != null ? "." + ext : "");

        java.nio.file.Path uploadDir = java.nio.file.Paths.get("uploads", "blog");
        java.nio.file.Files.createDirectories(uploadDir);

        java.nio.file.Path filePath = uploadDir.resolve(fileName);
        try (java.io.InputStream in = imageFile.getInputStream()) {
            java.nio.file.Files.copy(in, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return "/uploads/blog/" + fileName;
    }

}
