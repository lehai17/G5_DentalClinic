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

    @PostMapping("/save-draft")
    public String saveDraft(@ModelAttribute Blog blog, Authentication authentication) {
        User staff = userRepository.findByEmail(authentication.getName()).orElseThrow();
        workflowService.createDraft(blog, staff);
        return "redirect:/staff/blogs?saved=true";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Authentication authentication, Model model) {
        User staff = userRepository.findByEmail(authentication.getName()).orElseThrow();
        Blog blog = blogRepository.findById(id).orElseThrow();
        if (!blog.getCreatedBy().getId().equals(staff.getId())) return "redirect:/staff/blogs?forbidden=true";

        model.addAttribute("blog", blog);
        model.addAttribute("activePage", "blogs");
        return "staff/blog-form";
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id, @ModelAttribute Blog form, Authentication authentication) {
        User staff = userRepository.findByEmail(authentication.getName()).orElseThrow();
        Blog blog = blogRepository.findById(id).orElseThrow();
        workflowService.updateByStaff(blog, form, staff);
        return "redirect:/staff/blogs?updated=true";
    }

    @PostMapping("/{id}/submit")
    public String submit(@PathVariable Long id, Authentication authentication) {
        User staff = userRepository.findByEmail(authentication.getName()).orElseThrow();
        Blog blog = blogRepository.findById(id).orElseThrow();
        workflowService.submitForReview(blog, staff);
        return "redirect:/staff/blogs?submitted=true";
    }

    @PostMapping("/{id}/withdraw")
    public String withdraw(@PathVariable Long id, Authentication authentication) {
        User staff = userRepository.findByEmail(authentication.getName()).orElseThrow();
        Blog blog = blogRepository.findById(id).orElseThrow();
        workflowService.withdrawPending(blog, staff);
        return "redirect:/staff/blogs?withdrawn=true";
    }
}
