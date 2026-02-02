package com.dentalclinic.repository;

import com.dentalclinic.model.medical.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {

    Optional<MedicalRecord> findByAppointment_Id(Long appointmentId);

    Optional<MedicalRecord> findByAppointment_IdAndAppointment_Customer_User_Id(
            Long appointmentId,
            Long customerUserId
    );

    // ✅ history: lấy 10 record gần nhất của bệnh nhân
    List<MedicalRecord> findTop10ByAppointment_Customer_User_IdOrderByAppointment_DateDesc(Long customerUserId);
}
