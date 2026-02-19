package com.dentalclinic.controller.admin;

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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Controller
@RequestMapping("/admin/blogs")
public class AdminBlogController {

    private final BlogRepository blogRepository;
    private final UserRepository userRepository;
    private final BlogWorkflowService workflowService;

    public AdminBlogController(BlogRepository blogRepository,
                               UserRepository userRepository,
                               BlogWorkflowService workflowService) {
        this.blogRepository = blogRepository;
        this.userRepository = userRepository;
        this.workflowService = workflowService;
    }

    @GetMapping
    public String dashboard(@RequestParam(defaultValue = "PENDING") BlogStatus status,
                            @RequestParam(defaultValue = "0") int page,
                            Model model) {
        Pageable pageable = PageRequest.of(page, 10);

        model.addAttribute("blogPage", blogRepository.findByStatusOrderByUpdatedAtDesc(status, pageable));
        model.addAttribute("status", status);
        model.addAttribute("draftCount", blogRepository.countByStatus(BlogStatus.DRAFT));
        model.addAttribute("pendingCount", blogRepository.countByStatus(BlogStatus.PENDING));
        model.addAttribute("approvedCount", blogRepository.countByStatus(BlogStatus.APPROVED));
        model.addAttribute("rejectedCount", blogRepository.countByStatus(BlogStatus.REJECTED));
        model.addAttribute("activePage", "blogs");

        return "admin/blog/dashboard";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Blog blog = blogRepository.findById(id).orElseThrow();
        model.addAttribute("blog", blog);
        model.addAttribute("activePage", "blogs");
        return "admin/blog/detail";
    }

    // ====== Edit screen: ONLY PENDING/APPROVED ======
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Blog blog = blogRepository.findById(id).orElseThrow();

        // Admin chỉ được edit khi PENDING hoặc APPROVED
        if (!(blog.getStatus() == BlogStatus.PENDING || blog.getStatus() == BlogStatus.APPROVED)) {
            return "redirect:/admin/blogs?status=" + blog.getStatus() + "&editDenied=true";
        }

        model.addAttribute("blog", blog);
        model.addAttribute("activePage", "blogs");
        return "admin/blog/edit";
    }

    // ====== Update: ONLY PENDING/APPROVED, DO NOT change imageUrl ======
    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id,
                         @RequestParam String title,
                         @RequestParam String summary,
                         @RequestParam String content) {

        Blog blog = blogRepository.findById(id).orElseThrow();

        // Admin chỉ được edit khi PENDING hoặc APPROVED
        if (!(blog.getStatus() == BlogStatus.PENDING || blog.getStatus() == BlogStatus.APPROVED)) {
            return "redirect:/admin/blogs?status=" + blog.getStatus() + "&editDenied=true";
        }

        blog.setTitle(title);
        blog.setSummary(summary);
        blog.setContent(content);
        // KHÔNG set imageUrl => giữ nguyên ảnh

        blogRepository.save(blog);

        return "redirect:/admin/blogs/" + id + "?updated=true";
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id, Authentication authentication) {
        User admin = userRepository.findByEmail(authentication.getName()).orElseThrow();
        Blog blog = blogRepository.findById(id).orElseThrow();
        workflowService.approve(blog, admin);
        return "redirect:/admin/blogs?status=PENDING&approved=true";
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id,
                         @RequestParam String rejectionReason,
                         @RequestParam(required = false) BlogStatus returnStatus,
                         Authentication authentication) {

        User admin = userRepository.findByEmail(authentication.getName()).orElseThrow();
        Blog blog = blogRepository.findById(id).orElseThrow();

        workflowService.reject(blog, admin, rejectionReason);

        BlogStatus back = (returnStatus != null) ? returnStatus : BlogStatus.PENDING;
        return "redirect:/admin/blogs?status=" + back + "&rejected=true";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("blog", new Blog());
        model.addAttribute("activePage", "blogs");
        return "admin/blog/create";
    }
    @PostMapping("/create")
    public String create(@RequestParam String title,
                         @RequestParam String summary,
                         @RequestParam String content,
                         @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                         Authentication authentication) throws IOException {

        User admin = userRepository.findByEmail(authentication.getName()).orElseThrow();

        Blog blog = new Blog();
        blog.setTitle(title);
        blog.setSummary(summary);
        blog.setContent(content);
        blog.setCreatedBy(admin);

        // upload image (optional)
        if (imageFile != null && !imageFile.isEmpty()) {
            String ext = org.springframework.util.StringUtils
                    .getFilenameExtension(imageFile.getOriginalFilename());
            String fileName = java.util.UUID.randomUUID() + (ext != null ? "." + ext : "");

            // ✅ Lưu vào thư mục ngoài: uploads/blog (tính theo thư mục chạy project)
            java.nio.file.Path uploadDir = java.nio.file.Paths.get("uploads", "blog");
            java.nio.file.Files.createDirectories(uploadDir); // ✅ đảm bảo folder tồn tại

            java.nio.file.Path filePath = uploadDir.resolve(fileName);

            // ✅ dùng NIO copy (ổn hơn transferTo ở một số môi trường)
            try (java.io.InputStream in = imageFile.getInputStream()) {
                java.nio.file.Files.copy(in, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            blog.setImageUrl("/uploads/blog/" + fileName);
        }


        // auto approved
        blog.setStatus(BlogStatus.APPROVED);
        blog.setApprovedBy(admin);
        blog.setApprovedAt(java.time.LocalDateTime.now());
        blog.setRejectionReason(null);

        blogRepository.save(blog);

        return "redirect:/admin/blogs?status=APPROVED&created=true";
    }



}
