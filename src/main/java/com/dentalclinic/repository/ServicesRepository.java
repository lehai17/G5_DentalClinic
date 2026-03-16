package com.dentalclinic.repository;

import com.dentalclinic.model.service.Services;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

@Repository
public interface ServicesRepository extends JpaRepository<Services, Long> {
    List<Services> findByActiveTrueOrderByNameAsc();

    Optional<Services> findByNameIgnoreCase(String name);

    @Query("SELECT s FROM Services s WHERE " +
            "(:status IS NULL OR s.active = :status) AND " +
            "(:keyword IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Services> searchServices(@Param("keyword") String keyword, @Param("status") Boolean status);
}
