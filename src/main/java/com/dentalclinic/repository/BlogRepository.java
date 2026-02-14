package com.dentalclinic.repository;

import com.dentalclinic.model.blog.Blog;
import com.dentalclinic.model.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogRepository extends JpaRepository<Blog, Long> {

    Page<Blog> findByIsPublishedTrueOrderByCreatedAtDesc(Pageable pageable);

    // Blog chờ duyệt cho admin
    Page<Blog> findByIsPublishedFalseOrderByCreatedAtDesc(Pageable pageable);

    // Blog staff tự tạo (để staff quản lý bài của mình)
    Page<Blog> findByCreatedByOrderByCreatedAtDesc(User user, Pageable pageable);
}
