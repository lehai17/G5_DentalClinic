package com.dentalclinic.service;

import com.dentalclinic.exception.BlogValidationException;
import com.dentalclinic.model.blog.Blog;
import com.dentalclinic.model.blog.BlogStatus;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.BlogRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class BlogWorkflowService {

    private final BlogRepository blogRepository;

    public BlogWorkflowService(BlogRepository blogRepository) {
        this.blogRepository = blogRepository;
    }

    public Blog createDraft(Blog formData, User author) {
        Blog blog = new Blog();
        applyEditableFields(blog, formData);

        blog.setId(null);
        blog.setCreatedBy(author);
        blog.setStatus(BlogStatus.DRAFT);
        clearApprovalInfo(blog);

        return blogRepository.save(blog);
    }

    public Blog updateByStaff(Blog existingBlog, Blog formData, User staff) {
        validateOwner(existingBlog, staff);

        if (!(existingBlog.getStatus() == BlogStatus.DRAFT || existingBlog.getStatus() == BlogStatus.REJECTED)) {
            throw new IllegalStateException("Only DRAFT/REJECTED blog can be edited by staff.");
        }

        applyEditableFields(existingBlog, formData);

        if (existingBlog.getStatus() == BlogStatus.REJECTED) {
            existingBlog.setRejectionReason(null);
            existingBlog.setApprovedBy(null);
            existingBlog.setApprovedAt(null);
            existingBlog.setStatus(BlogStatus.DRAFT);
        }

        return blogRepository.save(existingBlog);
    }

    public Blog createAndApprove(Blog formData, User admin) {
        Blog blog = new Blog();
        applyEditableFields(blog, formData);

        blog.setId(null);
        blog.setCreatedBy(admin);
        blog.setStatus(BlogStatus.APPROVED);
        blog.setApprovedBy(admin);
        blog.setApprovedAt(LocalDateTime.now());
        blog.setRejectionReason(null);

        return blogRepository.save(blog);
    }

    public Blog updateAndApprove(Blog existingBlog, Blog formData, User admin) {
        applyEditableFields(existingBlog, formData);

        existingBlog.setStatus(BlogStatus.APPROVED);
        existingBlog.setApprovedBy(admin);
        existingBlog.setApprovedAt(LocalDateTime.now());
        existingBlog.setRejectionReason(null);

        return blogRepository.save(existingBlog);
    }

    public Blog submitForReview(Blog blog, User staff) {
        validateOwner(blog, staff);

        if (!(blog.getStatus() == BlogStatus.DRAFT || blog.getStatus() == BlogStatus.REJECTED)) {
            throw new IllegalStateException("Only DRAFT/REJECTED blog can be submitted.");
        }

        blog.setStatus(BlogStatus.PENDING);
        blog.setRejectionReason(null);
        blog.setApprovedBy(null);
        blog.setApprovedAt(null);

        return blogRepository.save(blog);
    }

    public Blog withdrawPending(Blog blog, User staff) {
        validateOwner(blog, staff);

        if (blog.getStatus() != BlogStatus.PENDING) {
            throw new IllegalStateException("Only PENDING blog can be withdrawn.");
        }

        blog.setStatus(BlogStatus.DRAFT);
        blog.setApprovedBy(null);
        blog.setApprovedAt(null);
        blog.setRejectionReason(null);

        return blogRepository.save(blog);
    }

    public Blog approve(Blog blog, User admin) {
        if (!(blog.getStatus() == BlogStatus.PENDING || blog.getStatus() == BlogStatus.DRAFT)) {
            throw new IllegalStateException("Only PENDING/DRAFT blog can be approved.");
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

        String cleanedReason = cleanRequired(reason, "Rejection reason");

        blog.setStatus(BlogStatus.REJECTED);
        blog.setApprovedBy(admin);
        blog.setApprovedAt(LocalDateTime.now());
        blog.setRejectionReason(cleanedReason);

        return blogRepository.save(blog);
    }

    private void validateOwner(Blog blog, User staff) {
        if (blog.getCreatedBy() == null || staff == null || blog.getCreatedBy().getId() == null || staff.getId() == null) {
            throw new SecurityException("Invalid blog owner.");
        }

        if (!blog.getCreatedBy().getId().equals(staff.getId())) {
            throw new SecurityException("You are not owner of this blog.");
        }
    }

    private void applyEditableFields(Blog target, Blog source) {
        if (target == null || source == null) {
            throw new IllegalArgumentException("Blog data must not be null.");
        }

        target.setTitle(cleanRequired(source.getTitle(), "Title"));
        target.setSummary(cleanRequired(source.getSummary(), "Summary"));
        target.setContent(cleanHtmlContent(source.getContent()));
        target.setImageUrl(cleanOptional(source.getImageUrl()));

        if (hasThumbnailField(target)) {
            target.setThumbnailUrl(cleanOptional(source.getThumbnailUrl()));
        }
    }

    private boolean hasThumbnailField(Blog blog) {
        return blog != null;
    }

    private void clearApprovalInfo(Blog blog) {
        blog.setApprovedBy(null);
        blog.setApprovedAt(null);
        blog.setRejectionReason(null);
    }

    private String cleanRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BlogValidationException(field + " must not be blank");
        }
        return value.trim();
    }

    private String cleanOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String cleanHtmlContent(String html) {
        if (html == null || html.isBlank()) {
            throw new BlogValidationException("Content must not be blank");
        }

        Document dirty = Jsoup.parseBodyFragment(html);

        // Xóa các tag nguy hiểm
        dirty.select("script, iframe, object, embed").remove();

        // Xóa các event handler kiểu onclick, onerror...
        for (Element element : dirty.getAllElements()) {
            element.attributes().asList().forEach(attr -> {
                String key = attr.getKey();
                if (key != null && key.toLowerCase().startsWith("on")) {
                    element.removeAttr(key);
                }
            });
        }

        // Giữ lại src của ảnh nếu là ảnh blog nội bộ hoặc link hợp lệ
        for (Element img : dirty.select("img")) {
            String src = img.attr("src");
            if (src != null) {
                src = src.trim();
            }

            if (src == null || src.isBlank()) {
                img.remove();
                continue;
            }

            boolean allowed =
                    src.startsWith("/uploads/blog/") ||
                            src.startsWith("http://") ||
                            src.startsWith("https://") ||
                            src.startsWith("data:image/");

            if (!allowed) {
                img.remove();
                continue;
            }

            img.attr("src", src);
        }

        String cleaned = dirty.body().html();

        if (cleaned == null || cleaned.isBlank()) {
            throw new BlogValidationException("Content must not be blank");
        }

        return cleaned.trim();
    }
}