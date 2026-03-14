package com.dentalclinic.service.admin;

import com.dentalclinic.dto.admin.AdminWalletOverviewDTO;
import com.dentalclinic.dto.admin.AdminWalletTransactionRowDTO;
import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.user.User;
import com.dentalclinic.model.wallet.Wallet;
import com.dentalclinic.model.wallet.WalletTransaction;
import com.dentalclinic.model.wallet.WalletTransactionType;
import com.dentalclinic.repository.WalletRepository;
import com.dentalclinic.repository.WalletTransactionRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminWalletService {

    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletRepository walletRepository;

    public AdminWalletService(WalletTransactionRepository walletTransactionRepository,
                              WalletRepository walletRepository) {
        this.walletTransactionRepository = walletTransactionRepository;
        this.walletRepository = walletRepository;
    }

    public AdminWalletOverviewDTO getOverview() {
        List<WalletTransaction> transactions = walletTransactionRepository.findAll();
        List<Wallet> wallets = walletRepository.findAll();

        BigDecimal totalDeposits = sumByType(transactions, WalletTransactionType.DEPOSIT);
        BigDecimal totalWithdrawals = sumByType(transactions, WalletTransactionType.WITHDRAW);
        BigDecimal totalPayments = sumByType(transactions, WalletTransactionType.PAYMENT);
        BigDecimal totalRefunds = sumByType(transactions, WalletTransactionType.REFUND);
        BigDecimal totalWalletBalance = wallets.stream()
                .map(Wallet::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AdminWalletOverviewDTO(
                totalDeposits,
                totalWithdrawals,
                totalPayments,
                totalRefunds,
                totalWalletBalance,
                transactions.size()
        );
    }

    public List<AdminWalletTransactionRowDTO> getTransactionRows(String keyword,
                                                                String type,
                                                                LocalDate dateFrom,
                                                                LocalDate dateTo) {
        String normalizedKeyword = normalize(keyword);
        String normalizedType = normalize(type);

        return walletTransactionRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(tx -> normalizedType.isBlank() || tx.getType().name().equalsIgnoreCase(normalizedType))
                .filter(tx -> dateFrom == null || !tx.getCreatedAt().toLocalDate().isBefore(dateFrom))
                .filter(tx -> dateTo == null || !tx.getCreatedAt().toLocalDate().isAfter(dateTo))
                .filter(tx -> matchesKeyword(tx, normalizedKeyword))
                .map(this::toTransactionRow)
                .toList();
    }

    public List<String> getSupportedTransactionTypes() {
        return Arrays.stream(WalletTransactionType.values())
                .map(Enum::name)
                .toList();
    }

    public Map<String, String> getSupportedTransactionTypeLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        for (WalletTransactionType type : WalletTransactionType.values()) {
            labels.put(type.name(), toTypeLabel(type));
        }
        return labels;
    }

    private BigDecimal sumByType(List<WalletTransaction> transactions, WalletTransactionType type) {
        return transactions.stream()
                .filter(tx -> tx.getType() == type)
                .map(WalletTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean matchesKeyword(WalletTransaction tx, String normalizedKeyword) {
        if (normalizedKeyword.isBlank()) {
            return true;
        }

        Wallet wallet = tx.getWallet();
        CustomerProfile customer = wallet != null ? wallet.getCustomer() : null;
        User user = customer != null ? customer.getUser() : null;

        return normalize(customer != null ? customer.getFullName() : "").contains(normalizedKeyword)
                || normalize(customer != null ? customer.getPhone() : "").contains(normalizedKeyword)
                || normalize(user != null ? user.getEmail() : "").contains(normalizedKeyword)
                || normalize(tx.getDescription()).contains(normalizedKeyword);
    }

    private AdminWalletTransactionRowDTO toTransactionRow(WalletTransaction tx) {
        Wallet wallet = tx.getWallet();
        CustomerProfile customer = wallet != null ? wallet.getCustomer() : null;
        User user = customer != null ? customer.getUser() : null;

        return new AdminWalletTransactionRowDTO(
                tx.getId(),
                customer != null ? customer.getFullName() : "--",
                user != null ? user.getEmail() : "--",
                customer != null ? customer.getPhone() : "--",
                tx.getType().name(),
                toTypeLabel(tx.getType()),
                tx.getStatus().name(),
                toStatusLabel(tx.getStatus().name()),
                tx.getAmount(),
                tx.getDescription(),
                tx.getCreatedAt()
        );
    }

    private String toTypeLabel(WalletTransactionType type) {
        return switch (type) {
            case DEPOSIT -> "Nap tien";
            case WITHDRAW -> "Rut tien";
            case PAYMENT -> "Thanh toan";
            case REFUND -> "Hoan tien";
            case ADJUSTMENT -> "Dieu chinh";
        };
    }

    private String toStatusLabel(String status) {
        return switch (status) {
            case "COMPLETED" -> "Hoan tat";
            case "PENDING" -> "Dang xu ly";
            case "FAILED" -> "That bai";
            case "CANCELLED" -> "Da huy";
            default -> status;
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
