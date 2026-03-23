package com.dentalclinic.controller.admin;

import com.dentalclinic.model.blog.Blog;
import com.dentalclinic.model.blog.BlogStatus;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.BlogRepository;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.BlogWorkflowService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


import java.io.InputStream;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;

import java.io.IOException;


@Controller
@RequestMapping("/admin/blogs")
public class AdminBlogController {

    private final BlogRepository blogRepository;
    private final UserRepository userRepository;
    private final BlogWorkflowService workflowService;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

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
    public String create(@ModelAttribute Blog form,
                         @RequestParam(value = "thumbnailFile", required = false) MultipartFile thumbnailFile,
                         @RequestParam(value = "existingImageUrl", required = false) String existingImageUrl,
                         Authentication authentication) throws IOException {

        User admin = userRepository.findByEmail(authentication.getName()).orElseThrow();

        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            form.setImageUrl(storeBlogImage(thumbnailFile));
        } else {
            form.setImageUrl(existingImageUrl);
        }

        // Bước 1: tạo theo logic giống staff
        Blog blog = workflowService.createAndApprove(form, admin);

        // Bước 2: admin auto duyệt luôn để hiện ngoài homepage
        workflowService.approve(blog, admin);

        // Nếu muốn admin quay về danh sách blog admin:
        return "redirect:/admin/blogs?status=APPROVED&created=true";


        // return "redirect:/homepage";
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

    @PostMapping(value = "/upload-inline-image", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> uploadInlineImage(@RequestParam("upload") MultipartFile file) throws IOException {
        String url = storeBlogImage(file);
        Map<String, Object> response = new HashMap<>();
        response.put("url", url);
        return response;
    }

}

