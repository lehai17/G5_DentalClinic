package com.dentalclinic.repository;

import com.dentalclinic.model.service.Services;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceRepository extends JpaRepository<Services, Long> {

    List<Services> findByActiveTrue();
}
