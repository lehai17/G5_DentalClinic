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

    // Public logic (Sá»¬A Lá»–I Táº I ï¿½ï¿½Y)
    // Thay vÃ¬ findByIsPublishedTrue, ta dÃ¹ng status = APPROVED
    Page<Blog> findByStatusOrderByApprovedAtDesc(BlogStatus status, Pageable pageable);

    // Náº¿u báº¡n váº«n muá»‘n giá»¯ tÃªn hÃ m tÆ°Æ¡ng tá»± cho ngáº¯n gá»n, dÃ¹ng Query:
    // @Query("SELECT b FROM Blog b WHERE b.status = com.dentalclinic.model.blog.BlogStatus.APPROVED")
    // Page<Blog> findAllPublished(Pageable pageable);
}
