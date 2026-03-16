package com.dentalclinic.controller;

import com.dentalclinic.repository.SlotRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.model.appointment.Slot;
import com.dentalclinic.model.profile.DentistProfile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@RestController
public class DiagnosticController {
    private final SlotRepository slotRepository;
    private final DentistProfileRepository dentistProfileRepository;

    public DiagnosticController(SlotRepository slotRepository, DentistProfileRepository dentistProfileRepository) {
        this.slotRepository = slotRepository;
        this.dentistProfileRepository = dentistProfileRepository;
    }

    @GetMapping("/api/diag")
    public Map<String, Object> diag() {
        Map<String, Object> res = new HashMap<>();

        long totalSlots = slotRepository.count();
        res.put("totalSlotsInDb", totalSlots);

        LocalDateTime start = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime end = start.plusDays(7);
        List<Slot> sample = slotRepository.findAllSlotsInRange(start, end);
        res.put("slotsNext7Days", sample.size());

        if (!sample.isEmpty()) {
            res.put("firstSlot", sample.get(0).getSlotTime().toString() + " active=" + sample.get(0).isActive());
        }

        long dentists = dentistProfileRepository.count();
        res.put("totalDentists", dentists);

        List<DentistProfile> active = dentistProfileRepository.findAvailableDentistsForDate(LocalDate.now());
        res.put("activeDentistsToday", active.size());

        return res;
    }
}
