package com.dentalclinic.controller.staff;

import com.dentalclinic.model.blog.Blog;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.BlogRepository;
import com.dentalclinic.repository.UserRepository;
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

    public StaffBlogController(BlogRepository blogRepository, UserRepository userRepository) {
        this.blogRepository = blogRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String listMyBlogs(Authentication authentication,
                              @RequestParam(defaultValue = "0") int page,
                              Model model) {
        String email = authentication.getName();
        User staff = userRepository.findByEmail(email).orElseThrow();

        Pageable pageable = PageRequest.of(page, 10);
        model.addAttribute("blogPage", blogRepository.findByCreatedByOrderByCreatedAtDesc(staff, pageable));
        model.addAttribute("pageTitle", "My Blogs");
        return "staff/blog-list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("blog", new Blog());
        model.addAttribute("pageTitle", "Create Blog");
        return "staff/blog-form";
    }

    @PostMapping
    public String createBlog(@ModelAttribute Blog blog, Authentication authentication) {
        String email = authentication.getName();
        User staff = userRepository.findByEmail(email).orElseThrow();

        blog.setCreatedBy(staff);
        blog.setPublished(false); // luôn chờ admin duyệt
        blogRepository.save(blog);

        return "redirect:/staff/blogs?created=true";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Authentication authentication, Model model) {
        String email = authentication.getName();
        User staff = userRepository.findByEmail(email).orElseThrow();

        Blog blog = blogRepository.findById(id).orElseThrow();

        // Chỉ cho sửa bài của chính staff và chưa publish
        if (!blog.getCreatedBy().getId().equals(staff.getId()) || blog.isPublished()) {
            return "redirect:/staff/blogs?forbidden=true";
        }

        model.addAttribute("blog", blog);
        return "staff/blog-form";
    }

    @PostMapping("/{id}")
    public String updateBlog(@PathVariable Long id, @ModelAttribute Blog form, Authentication authentication) {
        String email = authentication.getName();
        User staff = userRepository.findByEmail(email).orElseThrow();

        Blog blog = blogRepository.findById(id).orElseThrow();
        if (!blog.getCreatedBy().getId().equals(staff.getId()) || blog.isPublished()) {
            return "redirect:/staff/blogs?forbidden=true";
        }

        blog.setTitle(form.getTitle());
        blog.setSummary(form.getSummary());
        blog.setContent(form.getContent());
        blog.setImageUrl(form.getImageUrl());
        blog.setPublished(false); // cập nhật xong vẫn về chờ duyệt
        blogRepository.save(blog);

        return "redirect:/staff/blogs?updated=true";
    }

    @PostMapping("/{id}/delete")
    public String deleteBlog(@PathVariable Long id, Authentication authentication) {
        String email = authentication.getName();
        User staff = userRepository.findByEmail(email).orElseThrow();

        Blog blog = blogRepository.findById(id).orElseThrow();
        if (blog.getCreatedBy().getId().equals(staff.getId()) && !blog.isPublished()) {
            blogRepository.delete(blog);
        }

        return "redirect:/staff/blogs?deleted=true";
    }
}
