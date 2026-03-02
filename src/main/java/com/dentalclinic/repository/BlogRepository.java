package com.dentalclinic.repository;

import com.dentalclinic.model.blog.Blog;
import com.dentalclinic.model.blog.BlogStatus;
import com.dentalclinic.model.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BlogRepository extends JpaRepository<Blog, Long> {

    // Staff logic
    Page<Blog> findByCreatedByOrderByUpdatedAtDesc(User createdBy, Pageable pageable);
    Page<Blog> findByCreatedByAndStatusOrderByUpdatedAtDesc(User createdBy, BlogStatus status, Pageable pageable);

    // Admin logic
    Page<Blog> findByStatusOrderByUpdatedAtDesc(BlogStatus status, Pageable pageable);
    long countByStatus(BlogStatus status);

    // Public logic (SỬA LỖI TẠI ĐÂY)
    // Thay vì findByIsPublishedTrue, ta dùng status = APPROVED
    Page<Blog> findByStatusOrderByApprovedAtDesc(BlogStatus status, Pageable pageable);

    // Nếu bạn vẫn muốn giữ tên hàm tương tự cho ngắn gọn, dùng Query:
    // @Query("SELECT b FROM Blog b WHERE b.status = com.dentalclinic.model.blog.BlogStatus.APPROVED")
    // Page<Blog> findAllPublished(Pageable pageable);
}