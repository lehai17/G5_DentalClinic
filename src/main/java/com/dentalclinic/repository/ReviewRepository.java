package com.dentalclinic.repository;

import com.dentalclinic.model.review.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByAppointment_Id(Long appointmentId);

    Optional<Review> findByAppointment_Id(Long appointmentId);



    @Query("""
        select r from Review r
        where r.featuredOnHomepage = true
          and r.comment is not null
          and trim(r.comment) <> ''
        order by
          case when r.displayOrder is null then 999999 else r.displayOrder end asc,
          r.createdAt desc
    """)
    List<Review> findFeaturedHomepageReviews();

    @Query("""
        select r from Review r
        order by r.createdAt desc
    """)
    List<Review> findAllOrderByCreatedAtDesc();
}

