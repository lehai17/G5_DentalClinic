package com.dentalclinic.repository;

import com.dentalclinic.model.schedule.BusySchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DentistBusyScheduleRepository extends JpaRepository<BusySchedule, Long> {
    List<BusySchedule> findAllByOrderByCreatedAtDesc();
}