package com.dentalclinic.repository;

import com.dentalclinic.model.blog.Blog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BlogRepository extends JpaRepository<Blog, Long> {
    // Spring Boot sẽ tự hiểu lệnh này để lọc blog đã xuất bản và sắp xếp theo ngày
    List<Blog> findByIsPublishedTrueOrderByCreatedAtDesc();
}