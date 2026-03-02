package com.dentalclinic.repository;

import com.dentalclinic.model.profile.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

@Repository
public interface CustomerProfileRepository
                extends JpaRepository<CustomerProfile, Long> {

        Optional<CustomerProfile> findByUser_Id(Long userId);

        @Query("SELECT c FROM CustomerProfile c " +
                        "JOIN c.user u " +
                        "WHERE (:keyword IS NULL OR :keyword = '' OR " +
                        "       LOWER(c.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "       LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "       LOWER(c.phone) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                        "AND (:status IS NULL OR u.status = :status) " +
                        "AND (:serviceId IS NULL OR EXISTS (" +
                        "       SELECT 1 FROM Appointment a " +
                        "       WHERE a.customer = c AND a.service.id = :serviceId AND a.status = 'COMPLETED')) " +
                        "ORDER BY u.createdAt DESC")
        java.util.List<CustomerProfile> searchCustomers(
                        @org.springframework.data.repository.query.Param("keyword") String keyword,
                        @org.springframework.data.repository.query.Param("status") com.dentalclinic.model.user.UserStatus status,
                        @org.springframework.data.repository.query.Param("serviceId") Long serviceId);

        @Query("SELECT COUNT(c) FROM CustomerProfile c JOIN c.user u WHERE u.createdAt >= :startDate")
        long countNewCustomersSince(
                        @org.springframework.data.repository.query.Param("startDate") java.time.LocalDateTime startDate);

        @Query("SELECT COUNT(DISTINCT c) FROM CustomerProfile c " +
                        "WHERE EXISTS (SELECT 1 FROM Appointment a WHERE a.customer = c AND a.status IN ('PENDING', 'CONFIRMED'))")
        long countCustomersWithUpcomingAppointments();
}
