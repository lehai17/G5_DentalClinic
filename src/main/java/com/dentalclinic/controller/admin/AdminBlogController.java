package com.dentalclinic.controller.admin;

import com.dentalclinic.model.blog.Blog;
import com.dentalclinic.repository.BlogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/blogs")
public class AdminBlogController {

    private final BlogRepository blogRepository;

    public AdminBlogController(BlogRepository blogRepository) {
        this.blogRepository = blogRepository;
    }

    @GetMapping("/pending")
    public String pending(@RequestParam(defaultValue = "0") int page, Model model) {
        Pageable pageable = PageRequest.of(page, 10);
        model.addAttribute("blogPage", blogRepository.findByIsPublishedFalseOrderByCreatedAtDesc(pageable));
        return "admin/blog-pending";
    }

    @GetMapping("/published")
    public String published(@RequestParam(defaultValue = "0") int page, Model model) {
        Pageable pageable = PageRequest.of(page, 10);
        model.addAttribute("blogPage", blogRepository.findByIsPublishedTrueOrderByCreatedAtDesc(pageable));
        return "admin/blog-published";
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id) {
        Blog blog = blogRepository.findById(id).orElseThrow();
        blog.setPublished(true);
        blogRepository.save(blog);
        return "redirect:/admin/blogs/pending?approved=true";
    }

    @PostMapping("/{id}/unpublish")
    public String unpublish(@PathVariable Long id) {
        Blog blog = blogRepository.findById(id).orElseThrow();
        blog.setPublished(false);
        blogRepository.save(blog);
        return "redirect:/admin/blogs/published?unpublished=true";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        blogRepository.deleteById(id);
        return "redirect:/admin/blogs/pending?deleted=true";
    }
}
