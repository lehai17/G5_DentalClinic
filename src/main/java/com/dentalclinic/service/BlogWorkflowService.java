package com.dentalclinic.service;

import com.dentalclinic.exception.BlogValidationException;
import com.dentalclinic.model.blog.Blog;
import com.dentalclinic.model.blog.BlogStatus;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.BlogRepository;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class BlogWorkflowService {

    private final BlogRepository blogRepository;

    public BlogWorkflowService(BlogRepository blogRepository) {
        this.blogRepository = blogRepository;
    }

    /**
     * Staff/Admin tạo mới ở trạng thái DRAFT
     */
    public Blog createDraft(Blog formData, User author) {
        Blog blog = new Blog();
        applyEditableFields(blog, formData);

        blog.setId(null);
        blog.setCreatedBy(author);
        blog.setStatus(BlogStatus.DRAFT);
        clearApprovalInfo(blog);

        return blogRepository.save(blog);
    }

    /**
     * Staff chỉnh sửa bài của chính mình khi bài đang ở DRAFT hoặc REJECTED
     */
    public Blog updateByStaff(Blog existingBlog, Blog formData, User staff) {
        validateOwner(existingBlog, staff);

        if (!(existingBlog.getStatus() == BlogStatus.DRAFT || existingBlog.getStatus() == BlogStatus.REJECTED)) {
            throw new IllegalStateException("Only DRAFT/REJECTED blog can be edited by staff.");
        }

        applyEditableFields(existingBlog, formData);

        // Staff sửa lại bài bị reject thì xóa thông tin reject cũ
        if (existingBlog.getStatus() == BlogStatus.REJECTED) {
            existingBlog.setRejectionReason(null);
            existingBlog.setApprovedBy(null);
            existingBlog.setApprovedAt(null);
            existingBlog.setStatus(BlogStatus.DRAFT);
        }

        return blogRepository.save(existingBlog);
    }

    /**
     * Admin tạo bài và xuất bản ngay, không cần duyệt
     */
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

    /**
     * Admin chỉnh sửa bài rồi vẫn giữ published
     * Dùng khi admin muốn sửa nội dung bài PENDING/APPROVED mà không đổi ảnh nếu controller không truyền ảnh mới
     */
    public Blog updateAndApprove(Blog existingBlog, Blog formData, User admin) {
        applyEditableFields(existingBlog, formData);

        existingBlog.setStatus(BlogStatus.APPROVED);
        existingBlog.setApprovedBy(admin);
        existingBlog.setApprovedAt(LocalDateTime.now());
        existingBlog.setRejectionReason(null);

        return blogRepository.save(existingBlog);
    }

    /**
     * Staff gửi duyệt
     */
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

    /**
     * Staff rút bài đang chờ duyệt về draft
     */
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

    /**
     * Admin duyệt bài
     * Cho phép duyệt từ PENDING, và cũng cho phép duyệt trực tiếp từ DRAFT
     * để hỗ trợ luồng admin tạo bài không cần qua bước review.
     */
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

    /**
     * Admin từ chối bài
     */
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

        // Giữ đồng bộ nếu sau này bạn dùng thumbnailUrl riêng
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

        Safelist safelist = Safelist.relaxed()
                .addTags("img", "figure", "figcaption", "section", "article")
                .addAttributes("img", "src", "alt", "title", "width", "height", "style")
                .addAttributes(":all", "class", "style")
                .addProtocols("img", "src", "http", "https");

        return Jsoup.clean(html, safelist);
    }
}