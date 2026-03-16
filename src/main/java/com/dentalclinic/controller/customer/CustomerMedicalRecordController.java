package com.dentalclinic.controller.customer;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentDetail;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.medical.MedicalRecord;
import com.dentalclinic.model.payment.BillingNote;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.BillingNoteRepository;
import com.dentalclinic.repository.MedicalRecordRepository;
import com.dentalclinic.repository.UserRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class CustomerMedicalRecordController {

    private static final List<AppointmentStatus> RECORD_STATUSES = List.of(
            AppointmentStatus.DONE,
            AppointmentStatus.WAITING_PAYMENT,
            AppointmentStatus.COMPLETED
    );

    private final AppointmentRepository appointmentRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final BillingNoteRepository billingNoteRepository;
    private final UserRepository userRepository;

    public CustomerMedicalRecordController(AppointmentRepository appointmentRepository,
                                           MedicalRecordRepository medicalRecordRepository,
                                           BillingNoteRepository billingNoteRepository,
                                           UserRepository userRepository) {
        this.appointmentRepository = appointmentRepository;
        this.medicalRecordRepository = medicalRecordRepository;
        this.billingNoteRepository = billingNoteRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/patient/medical-records")
    public String medicalRecords(Authentication authentication,
                                 @RequestParam(required = false) String q,
                                 @RequestParam(required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                 @RequestParam(required = false, defaultValue = "1") int page,
                                 @RequestParam(required = false, defaultValue = "5") int size,
                                 Model model) {
        Long userId = resolveCurrentUserId(authentication);
        if (userId == null) {
            return "redirect:/login";
        }

        LocalDate fromDate = from;
        LocalDate toDate = to;
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            toDate = fromDate;
        }

        String keyword = normalizeSearch(q);

        final LocalDate finalFromDate = fromDate;
        final LocalDate finalToDate = toDate;
        final String finalKeyword = keyword;

        List<Appointment> allAppointments = appointmentRepository
                .findByCustomer_User_IdOrderByDateDesc(userId)
                .stream()
                .filter(a -> a.getStatus() != null && RECORD_STATUSES.contains(a.getStatus()))
                .toList();

        List<Appointment> uniqueAppointments = allAppointments.stream()
                .filter(a -> a.getId() != null)
                .collect(Collectors.toMap(Appointment::getId, a -> a, (a, b) -> a, LinkedHashMap::new))
                .values()
                .stream()
                .toList();

        List<ChainRaw> chains = buildChains(uniqueAppointments);
        List<ChainRaw> filtered = chains.stream()
                .filter(chain -> matchesDate(chain, finalFromDate, finalToDate))
                .filter(chain -> matchesSearch(chain, finalKeyword))
                .toList();

        long totalVisits = filtered.stream()
                .mapToLong(c -> c.steps().size())
                .sum();
        long singleCount = filtered.stream().filter(c -> c.steps().size() == 1).count();
        long chainCount = filtered.size() - singleCount;

        int safeSize = Math.max(1, size);
        int totalChains = filtered.size();
        int totalPages = (int) Math.ceil(totalChains / (double) safeSize);
        int safePage = Math.max(1, Math.min(page, Math.max(totalPages, 1)));
        int fromIndex = Math.min((safePage - 1) * safeSize, totalChains);
        int toIndex = Math.min(fromIndex + safeSize, totalChains);
        List<ChainRaw> pageChains = filtered.subList(fromIndex, toIndex);

        Set<Long> appointmentIds = pageChains.stream()
                .flatMap(chain -> chain.steps().stream())
                .map(Appointment::getId)
                .collect(Collectors.toSet());

        Map<Long, MedicalRecord> medicalRecordMap = new LinkedHashMap<>();
        Map<Long, BillingNote> billingNoteMap = new LinkedHashMap<>();
        if (!appointmentIds.isEmpty()) {
            Map<Long, MedicalRecord> fetchedRecords = medicalRecordRepository
                    .findByAppointment_IdInWithDetails(new ArrayList<>(appointmentIds))
                    .stream()
                    .filter(mr -> mr.getAppointment() != null)
                    .collect(Collectors.toMap(
                            mr -> mr.getAppointment().getId(),
                            mr -> mr,
                            (a, b) -> a,
                            LinkedHashMap::new
                    ));
            medicalRecordMap.putAll(fetchedRecords);

            Map<Long, BillingNote> fetchedBilling = billingNoteRepository
                    .findByAppointment_IdInWithPrescriptions(new ArrayList<>(appointmentIds))
                    .stream()
                    .filter(bn -> bn.getAppointment() != null)
                    .collect(Collectors.toMap(
                            bn -> bn.getAppointment().getId(),
                            bn -> bn,
                            (a, b) -> a,
                            LinkedHashMap::new
                    ));
            billingNoteMap.putAll(fetchedBilling);
        }

        List<ChainView> chainViews = pageChains.stream()
                .map(chain -> {
                    List<ChainStepView> steps = chain.steps().stream()
                            .sorted(Comparator.comparing(Appointment::getDate)
                                    .thenComparing(Appointment::getStartTime, Comparator.nullsLast(LocalTime::compareTo)))
                            .map(appt -> new ChainStepView(
                                    appt,
                                    buildServiceLabel(appt),
                                    medicalRecordMap.get(appt.getId()),
                                    billingNoteMap.get(appt.getId())
                            ))
                            .toList();
                    return new ChainView(chain.root(), steps);
                })
                .toList();

        model.addAttribute("chains", chainViews);
        model.addAttribute("search", keyword);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("totalVisits", totalVisits);
        model.addAttribute("singleCount", singleCount);
        model.addAttribute("chainCount", chainCount);
        model.addAttribute("page", safePage);
        model.addAttribute("pageSize", safeSize);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalChains", totalChains);
        model.addAttribute("active", "records");

        return "customer/medical-records";
    }

    private Long resolveCurrentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userRepository.findByEmail(userDetails.getUsername())
                    .map(user -> user.getId())
                    .orElse(null);
        }
        return userRepository.findByEmail(authentication.getName())
                .map(user -> user.getId())
                .orElse(null);
    }

    private boolean matchesDate(ChainRaw chain, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return true;
        }
        return chain.steps().stream().anyMatch(appt -> {
            LocalDate date = appt.getDate();
            if (date == null) return false;
            boolean afterFrom = from == null || !date.isBefore(from);
            boolean beforeTo = to == null || !date.isAfter(to);
            return afterFrom && beforeTo;
        });
    }

    private boolean matchesSearch(ChainRaw chain, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return true;
        }
        String lower = keyword.toLowerCase(Locale.ROOT);
        return chain.steps().stream().anyMatch(appt -> {
            String idText = appt.getId() != null ? String.valueOf(appt.getId()) : "";
            String serviceLabel = buildServiceLabel(appt);
            String dentistName = appt.getDentist() != null ? appt.getDentist().getFullName() : "";
            return idText.contains(lower)
                    || (serviceLabel != null && serviceLabel.toLowerCase(Locale.ROOT).contains(lower))
                    || (dentistName != null && dentistName.toLowerCase(Locale.ROOT).contains(lower));
        });
    }

    private String normalizeSearch(String q) {
        if (q == null) {
            return "";
        }
        String trimmed = q.trim();
        return trimmed.isBlank() ? "" : trimmed;
    }

    private List<ChainRaw> buildChains(List<Appointment> appointments) {
        Map<Long, Appointment> byId = appointments.stream()
                .filter(a -> a.getId() != null)
                .collect(Collectors.toMap(Appointment::getId, a -> a, (a, b) -> a));

        Map<Long, List<Appointment>> grouped = new LinkedHashMap<>();
        for (Appointment appt : appointments) {
            Long rootId = findRootId(appt, byId);
            grouped.computeIfAbsent(rootId, k -> new ArrayList<>()).add(appt);
        }

        List<ChainRaw> chains = new ArrayList<>();
        for (Map.Entry<Long, List<Appointment>> entry : grouped.entrySet()) {
            List<Appointment> steps = entry.getValue();
            steps.sort(Comparator.comparing(Appointment::getDate)
                    .thenComparing(Appointment::getStartTime, Comparator.nullsLast(LocalTime::compareTo)));
            Appointment root = steps.get(0);
            chains.add(new ChainRaw(root, steps));
        }
        chains.sort(Comparator.comparing((ChainRaw c) -> c.root().getDate(), Comparator.nullsLast(LocalDate::compareTo))
                .thenComparing(c -> c.root().getStartTime(), Comparator.nullsLast(LocalTime::compareTo))
                .reversed());
        return chains;
    }

    private Long findRootId(Appointment appt, Map<Long, Appointment> byId) {
        Appointment current = appt;
        while (current != null && current.getOriginalAppointment() != null) {
            Appointment parent = current.getOriginalAppointment();
            if (parent.getId() == null) {
                break;
            }
            Appointment inMap = byId.get(parent.getId());
            if (inMap == null || Objects.equals(inMap.getId(), current.getId())) {
                break;
            }
            current = inMap;
        }
        return current != null && current.getId() != null ? current.getId() : appt.getId();
    }

    private String buildServiceLabel(Appointment appointment) {
        if (appointment == null) {
            return "";
        }

        List<AppointmentDetail> details = appointment.getAppointmentDetails();
        if (details != null && !details.isEmpty()) {
            String joined = details.stream()
                    .sorted(Comparator.comparing(
                            AppointmentDetail::getDetailOrder,
                            Comparator.nullsLast(Integer::compareTo)
                    ))
                    .map(detail -> {
                        String name = detail.getServiceNameSnapshot();
                        if (name == null || name.isBlank()) {
                            if (detail.getService() != null) {
                                name = detail.getService().getName();
                            }
                        }
                        return name;
                    })
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .collect(Collectors.joining(", "));

            if (!joined.isBlank()) {
                return joined;
            }
        }

        if (appointment.getService() != null && appointment.getService().getName() != null) {
            return appointment.getService().getName();
        }

        return "";
    }

    private record ChainRaw(Appointment root, List<Appointment> steps) {}

    public record ChainStepView(
            Appointment appointment,
            String serviceLabel,
            MedicalRecord medicalRecord,
            BillingNote billingNote
    ) {}

    public record ChainView(
            Appointment root,
            List<ChainStepView> steps
    ) {}
}
