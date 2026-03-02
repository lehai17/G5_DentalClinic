package com.dentalclinic.repository;

import com.dentalclinic.model.blog.Blog;
import com.dentalclinic.model.blog.BlogStatus;
import com.dentalclinic.model.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogRepository extends JpaRepository<Blog, Long> {

    // Staff
    Page<Blog> findByCreatedByOrderByUpdatedAtDesc(User createdBy, Pageable pageable);
    Page<Blog> findByCreatedByAndStatusOrderByUpdatedAtDesc(User createdBy, BlogStatus status, Pageable pageable);

    // Admin
    Page<Blog> findByStatusOrderByUpdatedAtDesc(BlogStatus status, Pageable pageable);
    long countByStatus(BlogStatus status);

    // Public
    Page<Blog> findByStatusOrderByApprovedAtDesc(BlogStatus status, Pageable pageable);
    // Spring Boot sẽ tự hiểu lệnh này để lọc blog đã xuất bản và sắp xếp theo ngày
    Page<Blog> findByIsPublishedTrueOrderByCreatedAtDesc(Pageable pageable);
}
