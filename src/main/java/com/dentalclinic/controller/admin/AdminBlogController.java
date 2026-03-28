package com.dentalclinic.controller.admin;

import com.dentalclinic.model.blog.Blog;
import com.dentalclinic.model.blog.BlogStatus;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.BlogRepository;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.BlogWorkflowService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Controller
@RequestMapping("/admin/blogs")
public class AdminBlogController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

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
    public String dashboard(
            @RequestParam(value = "status", required = false) BlogStatus status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "fromDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Model model
    ) {
        Pageable pageable = PageRequest.of(page, 10, Sort.by(Sort.Direction.DESC, "updatedAt"));

        Specification<Blog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (email != null && !email.isBlank()) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("createdBy").get("email")),
                                "%" + email.trim().toLowerCase() + "%"
                        )
                );
            }

            if (fromDate != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(root.get("updatedAt"), fromDate.atStartOfDay())
                );
            }

            if (toDate != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(root.get("updatedAt"), toDate.plusDays(1).atStartOfDay().minusNanos(1))
                );
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Blog> blogPage = blogRepository.findAll(spec, pageable);

        model.addAttribute("blogPage", blogPage);
        model.addAttribute("selectedStatus", status == null ? "ALL" : status.name());

        model.addAttribute("draftCount", blogRepository.countByStatus(BlogStatus.DRAFT));
        model.addAttribute("pendingCount", blogRepository.countByStatus(BlogStatus.PENDING));
        model.addAttribute("approvedCount", blogRepository.countByStatus(BlogStatus.APPROVED));
        model.addAttribute("rejectedCount", blogRepository.countByStatus(BlogStatus.REJECTED));
        model.addAttribute("activePage", "blogs");

        model.addAttribute("email", email);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);

        return "admin/blog/dashboard";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id, Model model) {
        Blog blog = blogRepository.findById(id).orElseThrow();
        model.addAttribute("blog", blog);
        model.addAttribute("activePage", "blogs");
        return "admin/blog/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable("id") Long id, Model model) {
        Blog blog = blogRepository.findById(id).orElseThrow();

        if (!(blog.getStatus() == BlogStatus.PENDING || blog.getStatus() == BlogStatus.APPROVED)) {
            return "redirect:/admin/blogs?status=" + blog.getStatus() + "&editDenied=true";
        }

        model.addAttribute("blog", blog);
        model.addAttribute("activePage", "blogs");
        return "admin/blog/edit";
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable("id") Long id,
                         @RequestParam("title") String title,
                         @RequestParam("summary") String summary,
                         @RequestParam("content") String content) {

        Blog blog = blogRepository.findById(id).orElseThrow();

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
    public String approve(@PathVariable("id") Long id, Authentication authentication) {
        User admin = userRepository.findByEmail(authentication.getName()).orElseThrow();
        Blog blog = blogRepository.findById(id).orElseThrow();
        workflowService.approve(blog, admin);
        return "redirect:/admin/blogs?status=PENDING&approved=true";
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable("id") Long id,
                         @RequestParam("rejectionReason") String rejectionReason,
                         @RequestParam(value = "returnStatus", required = false) BlogStatus returnStatus,
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
                         Authentication authentication,
                         Model model) throws IOException {

        User admin = userRepository.findByEmail(authentication.getName()).orElseThrow();

        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            form.setImageUrl(storeBlogImage(thumbnailFile));
        } else {
            form.setImageUrl(existingImageUrl);
        }

        if (!StringUtils.hasText(form.getImageUrl())) {
            model.addAttribute("blog", form);
            model.addAttribute("errorMessage", "Vui lòng chọn ảnh thumbnail cho bài viết.");
            model.addAttribute("activePage", "blogs");
            return "admin/blog/create";
        }

        workflowService.createAndApprove(form, admin);

        return "redirect:/admin/blogs?status=APPROVED&created=true";
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