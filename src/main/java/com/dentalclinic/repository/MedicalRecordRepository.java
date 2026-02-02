package com.dentalclinic.repository;

import com.dentalclinic.model.medical.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {

    // ✅ LẤY medical record theo appointment
    Optional<MedicalRecord> findByAppointment_Id(Long appointmentId);

    // ✅ customer xem record của chính mình
    Optional<MedicalRecord> findByAppointment_IdAndAppointment_Customer_User_Id(
            Long appointmentId,
            Long customerUserId
    );

    // ✅ lịch sử khám của customer (mới nhất trước)
    List<MedicalRecord> findTop10ByAppointment_Customer_User_IdOrderByAppointment_DateDesc(
            Long customerUserId
    );
}
