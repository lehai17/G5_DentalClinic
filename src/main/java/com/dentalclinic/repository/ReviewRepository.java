package com.dentalclinic.repository;

import com.dentalclinic.model.review.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByAppointment_Id(Long appointmentId);

    Optional<Review> findByAppointment_Id(Long appointmentId);

    @Query("""
    select r from Review r
    where r.featuredOnHomepage = true
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

    @Query("""
        select r from Review r
        left join r.customer c
        left join r.service s
        left join r.dentist d
        where (:customerName is null or lower(c.fullName) like lower(concat('%', :customerName, '%')))
          and (:serviceName is null or lower(s.name) like lower(concat('%', :serviceName, '%')))
          and (:dentistName is null or lower(d.fullName) like lower(concat('%', :dentistName, '%')))
          and (:fromDate is null or r.createdAt >= :fromDate)
          and (:toDate is null or r.createdAt <= :toDate)
        order by r.createdAt desc
    """)
    List<Review> searchReviewsForStaff(
            @Param("customerName") String customerName,
            @Param("serviceName") String serviceName,
            @Param("dentistName") String dentistName,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate
    );
}