package com.dentalclinic.config;

import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.model.schedule.DentistSchedule;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.repository.DentistScheduleRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Seed khung giờ bác sĩ (dentist_schedule) để bước 2 "Chọn giờ" có dữ liệu.
 * Chỉ chạy khi chưa có lịch nào trong 7 ngày tới.
 */
@Configuration
public class DentistScheduleSeeder {

    @Bean
    public ApplicationRunner seedDentistSchedulesIfEmpty(
            DentistScheduleRepository scheduleRepo,
            DentistProfileRepository dentistRepo) {
        return args -> {
            List<DentistSchedule> existing = scheduleRepo.findAll();
            if (!existing.isEmpty()) return;

            List<DentistProfile> dentists = dentistRepo.findAll();
            if (dentists.isEmpty()) return;

            DentistProfile dentist = dentists.get(0);
            LocalDate today = LocalDate.now();
            for (int day = 0; day < 7; day++) {
                LocalDate date = today.plusDays(day);
                for (int hour = 8; hour < 18; hour++) {
                    DentistSchedule slot = new DentistSchedule();
                    slot.setDentist(dentist);
                    slot.setDate(date);
                    slot.setStartTime(LocalTime.of(hour, 0));
                    slot.setEndTime(LocalTime.of(hour + 1, 0));
                    slot.setAvailable(true);
                    scheduleRepo.save(slot);
                }
            }
        };
    }
}
