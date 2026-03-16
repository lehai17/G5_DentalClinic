package com.dentalclinic.service.admin;

import com.dentalclinic.dto.admin.AdminAgendaItemDto;
import com.dentalclinic.dto.admin.AdminDashboardStatsDTO;
import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.wallet.WalletTransaction;
import com.dentalclinic.model.wallet.WalletTransactionType;
import com.dentalclinic.model.wallet.WalletTransactionStatus;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.CustomerProfileRepository;
import com.dentalclinic.repository.WalletTransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminDashboardService {

    private final AppointmentRepository appointmentRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    public AdminDashboardService(AppointmentRepository appointmentRepository,
            CustomerProfileRepository customerProfileRepository,
            WalletTransactionRepository walletTransactionRepository) {
        this.appointmentRepository = appointmentRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.walletTransactionRepository = walletTransactionRepository;
    }

    public AdminDashboardStatsDTO getDashboardStats() {
        AdminDashboardStatsDTO stats = new AdminDashboardStatsDTO();

        // 1. Basic Counts
        stats.setTotalCustomers(customerProfileRepository.count());
        stats.setTotalAppointments(appointmentRepository.count());

        List<AppointmentStatus> pendingStatuses = List.of(AppointmentStatus.PENDING, AppointmentStatus.PENDING_DEPOSIT);
        stats.setPendingAppointments(appointmentRepository.countByStatusIn(pendingStatuses));

        // 2. Revenue Calculation (Sum of all completed PAYMENT transactions)
        List<WalletTransaction> allPayments = walletTransactionRepository.findAll().stream()
                .filter(tx -> tx.getType() == WalletTransactionType.PAYMENT
                        && tx.getStatus() == WalletTransactionStatus.COMPLETED)
                .collect(Collectors.toList());

        BigDecimal totalRevenue = allPayments.stream()
                .map(WalletTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.setTotalRevenue(totalRevenue);

        // 3. Growth Rate (Customers this month vs total)
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        long newCustomers = customerProfileRepository.countNewCustomersSince(startOfMonth);
        double growth = stats.getTotalCustomers() > 0 ? (double) newCustomers / stats.getTotalCustomers() * 100 : 0;
        stats.setGrowthRate(growth);

        // 4. Recent Appointments (Last 7)
        List<Appointment> recentRaw = appointmentRepository.findAll().stream()
                .sorted(Comparator.comparing(Appointment::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(7)
                .collect(Collectors.toList());

        stats.setRecentAppointments(recentRaw.stream().map(this::toAgendaDto).collect(Collectors.toList()));

        // 5. Revenue Trend (Last 7 days)
        Map<String, BigDecimal> trend = new LinkedHashMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
        for (int i = 6; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            BigDecimal dayRevenue = allPayments.stream()
                    .filter(tx -> tx.getCreatedAt().toLocalDate().equals(d))
                    .map(WalletTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            trend.put(d.format(fmt), dayRevenue);
        }
        stats.setRevenueTrend(trend);

        // 6. Service Distribution
        Map<String, Long> distribution = appointmentRepository.findAll().stream()
                .filter(a -> a.getService() != null)
                .collect(Collectors.groupingBy(a -> a.getService().getName(), Collectors.counting()));
        stats.setServiceDistribution(distribution);

        return stats;
    }

    private AdminAgendaItemDto toAgendaDto(Appointment a) {
        AdminAgendaItemDto dto = new AdminAgendaItemDto(
                a.getId(),
                a.getStartTime(),
                a.getEndTime(),
                a.getCustomer() != null && a.getCustomer().getUser() != null ? a.getCustomer().getUser().getId() : null,
                a.getCustomer() != null ? a.getCustomer().getFullName() : null,
                a.getDentist() != null ? a.getDentist().getId() : null,
                a.getDentist() != null ? a.getDentist().getFullName() : null,
                a.getService() != null ? a.getService().getName() : null,
                a.getStatus() != null ? a.getStatus().name() : "N/A");
        dto.setAvailable(false);
        return dto;
    }
}
