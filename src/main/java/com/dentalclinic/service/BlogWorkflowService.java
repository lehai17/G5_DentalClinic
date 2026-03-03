package com.dentalclinic.service;

import com.dentalclinic.model.blog.Blog;
import com.dentalclinic.model.blog.BlogStatus;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.BlogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class BlogWorkflowService {

    private final BlogRepository blogRepository;

    public BlogWorkflowService(BlogRepository blogRepository) {
        this.blogRepository = blogRepository;
    }

    public Blog createDraft(Blog blog, User staff) {
        blog.setId(null);
        blog.setCreatedBy(staff);

        blog.setTitle(cleanRequired(blog.getTitle(), "Title"));
        blog.setSummary(cleanRequired(blog.getSummary(), "Summary"));
        blog.setContent(cleanRequired(blog.getContent(), "Content"));
        blog.setImageUrl(cleanOptional(blog.getImageUrl()));

        blog.setStatus(BlogStatus.DRAFT);
        blog.setApprovedBy(null);
        blog.setApprovedAt(null);
        blog.setRejectionReason(null);
        return blogRepository.save(blog);
    }

    public Blog updateByStaff(Blog blog, Blog formData, User staff) {
        validateOwner(blog, staff);

        if (!(blog.getStatus() == BlogStatus.DRAFT || blog.getStatus() == BlogStatus.REJECTED)) {
            throw new IllegalStateException("Only DRAFT/REJECTED blog can be edited by staff.");
        }

        blog.setTitle(cleanRequired(formData.getTitle(), "Title"));
        blog.setSummary(cleanRequired(formData.getSummary(), "Summary"));
        blog.setContent(cleanRequired(formData.getContent(), "Content"));
        blog.setImageUrl(cleanOptional(formData.getImageUrl()));

        return blogRepository.save(blog);
    }

    public Blog submitForReview(Blog blog, User staff) {
        validateOwner(blog, staff);

        if (!(blog.getStatus() == BlogStatus.DRAFT || blog.getStatus() == BlogStatus.REJECTED)) {
            throw new IllegalStateException("Only DRAFT/REJECTED blog can be submitted.");
        }

        blog.setStatus(BlogStatus.PENDING);
        blog.setRejectionReason(null);
        return blogRepository.save(blog);
    }

    public Blog withdrawPending(Blog blog, User staff) {
        validateOwner(blog, staff);

        if (blog.getStatus() != BlogStatus.PENDING) {
            throw new IllegalStateException("Only PENDING blog can be withdrawn.");
        }

        blog.setStatus(BlogStatus.DRAFT);
        return blogRepository.save(blog);
    }

    public Blog approve(Blog blog, User admin) {
        if (blog.getStatus() != BlogStatus.PENDING) {
            throw new IllegalStateException("Only PENDING blog can be approved.");
        }
        blog.setStatus(BlogStatus.APPROVED);
        blog.setApprovedBy(admin);
        blog.setApprovedAt(LocalDateTime.now());
        blog.setRejectionReason(null);
        return blogRepository.save(blog);
    }

    public Blog reject(Blog blog, User admin, String reason) {
        if (!(blog.getStatus() == BlogStatus.PENDING || blog.getStatus() == BlogStatus.APPROVED)) {
            throw new IllegalStateException("Only PENDING/APPROVED blog can be rejected.");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Rejection reason is required.");
        }
        blog.setStatus(BlogStatus.REJECTED);
        blog.setApprovedBy(admin);
        blog.setApprovedAt(LocalDateTime.now());
        blog.setRejectionReason(reason.trim());
        return blogRepository.save(blog);
    }

    private void validateOwner(Blog blog, User staff) {
        if (!blog.getCreatedBy().getId().equals(staff.getId())) {
            throw new SecurityException("You are not owner of this blog.");
        }
    }
    private String cleanRequired(String s, String field) {
        if (s == null || s.isBlank()) {   // isBlank chặn luôn toàn space
            throw new com.dentalclinic.exception.BlogValidationException(field + " must not be blank");
        }
        return s.trim();
    }

    private String cleanOptional(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
