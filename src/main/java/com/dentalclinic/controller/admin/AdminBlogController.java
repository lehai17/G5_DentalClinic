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
                         Authentication authentication) {
        User admin = userRepository.findByEmail(authentication.getName()).orElseThrow();
        Blog blog = blogRepository.findById(id).orElseThrow();
        workflowService.reject(blog, admin, rejectionReason);
        return "redirect:/admin/blogs?status=PENDING&rejected=true";
    }
}
