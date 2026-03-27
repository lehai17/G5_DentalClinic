package com.dentalclinic.controller.staff;

import com.dentalclinic.exception.BlogValidationException;
import com.dentalclinic.model.blog.Blog;
import com.dentalclinic.model.blog.BlogStatus;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.BlogRepository;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.BlogWorkflowService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Controller
@RequestMapping("/staff/blogs")
public class StaffBlogController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

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

    @PostMapping("/save-draft")
    public String saveDraft(@ModelAttribute Blog form,
                            @RequestParam(value = "thumbnailFile", required = false) MultipartFile thumbnailFile,
                            @RequestParam(value = "existingImageUrl", required = false) String existingImageUrl,
                            Authentication authentication,
                            Model model) throws IOException {

        User staff = userRepository.findByEmail(authentication.getName()).orElseThrow();

        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            form.setImageUrl(storeBlogImage(thumbnailFile));
        } else {
            form.setImageUrl(existingImageUrl);
        }

        try {
            if (form.getId() == null) {
                workflowService.createDraft(form, staff);
            } else {
                Blog existing = blogRepository.findById(form.getId()).orElseThrow();
                if (!existing.getCreatedBy().getId().equals(staff.getId())) {
                    return "redirect:/staff/blogs?forbidden=true";
                }
                workflowService.updateByStaff(existing, form, staff);
            }
        } catch (BlogValidationException ex) {
            return renderFormWithError(model, form, ex.getMessage());
        }

        return "redirect:/staff/blogs?saved=true";
    }

    @PostMapping("/submit-review")
    public String submitReview(@ModelAttribute Blog form,
                               @RequestParam(value = "thumbnailFile", required = false) MultipartFile thumbnailFile,
                               @RequestParam(value = "existingImageUrl", required = false) String existingImageUrl,
                               Authentication authentication,
                               Model model) throws IOException {

        User staff = userRepository.findByEmail(authentication.getName()).orElseThrow();

        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            form.setImageUrl(storeBlogImage(thumbnailFile));
        } else {
            form.setImageUrl(existingImageUrl);
        }

        Blog target;
        try {
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
        } catch (BlogValidationException ex) {
            return renderFormWithError(model, form, ex.getMessage());
        }

        return "redirect:/staff/blogs?submitted=true";
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

    @PostMapping(value = "/upload-inline-image", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> uploadInlineImage(@RequestParam("upload") MultipartFile file) throws IOException {
        String url = storeBlogImage(file);

        Map<String, Object> response = new HashMap<>();
        response.put("url", url);
        return response;
    }

    private String renderFormWithError(Model model, Blog form, String errorMessage) {
        model.addAttribute("blog", form);
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("activePage", "blogs");
        return "staff/blog-form";
    }

    private void assertOwner(Blog blog, User staff) {
        if (!blog.getCreatedBy().getId().equals(staff.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private String storeBlogImage(MultipartFile imageFile) throws IOException {
        if (imageFile == null || imageFile.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }

        String ext = StringUtils.getFilenameExtension(imageFile.getOriginalFilename());
        ext = ext != null ? ext.toLowerCase() : "";

        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only jpg, jpeg, png, webp, gif are allowed");
        }

        String fileName = UUID.randomUUID() + "." + ext;

        Path uploadDir = Paths.get("uploads", "blog");
        Files.createDirectories(uploadDir);

        Path filePath = uploadDir.resolve(fileName);

        try (InputStream in = imageFile.getInputStream()) {
            Files.copy(in, filePath, StandardCopyOption.REPLACE_EXISTING);
        }

        return "/uploads/blog/" + fileName;
    }
}