package com.dentalclinic.service.wallet;

import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.wallet.Wallet;
import com.dentalclinic.model.wallet.WalletTransaction;
import com.dentalclinic.model.wallet.WalletTransactionStatus;
import com.dentalclinic.model.wallet.WalletTransactionType;
import com.dentalclinic.repository.CustomerProfileRepository;
import com.dentalclinic.repository.WalletRepository;
import com.dentalclinic.repository.WalletTransactionRepository;
import com.dentalclinic.service.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class WalletService {
    public static final BigDecimal WALLET_TOPUP_CREDIT_RATE = BigDecimal.ONE;
    public static final BigDecimal DAILY_WITHDRAWAL_LIMIT = new BigDecimal("5000000.00");
//    public static final BigDecimal WALLET_TOPUP_CREDIT_RATE = new BigDecimal("0.95");
//    public static final BigDecimal WALLET_TOPUP_FIXED_FEE = BigDecimal.valueOf(5_000L);



    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final NotificationService notificationService;
    private final CustomerProfileRepository customerProfileRepository;

    public WalletService(WalletRepository walletRepository,
                         WalletTransactionRepository walletTransactionRepository,
                         NotificationService notificationService,
                         CustomerProfileRepository customerProfileRepository) {
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.notificationService = notificationService;
        this.customerProfileRepository = customerProfileRepository;
    }

    @Transactional
    public Wallet getOrCreateWallet(CustomerProfile customer) {
        return walletRepository.findByCustomer_Id(customer.getId())
                .orElseGet(() -> walletRepository.save(
                        Wallet.builder()
                                .customer(customer)
                                .balance(BigDecimal.ZERO)
                                .build()
                ));
    }

    @Transactional
    public void deposit(CustomerProfile customer, BigDecimal amount, String description, Long appointmentId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("So tien phai lon hon 0");
        }

        Wallet wallet = getOrCreateWallet(customer);
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        walletTransactionRepository.save(
                WalletTransaction.builder()
                        .wallet(wallet)
                        .type(WalletTransactionType.DEPOSIT)
                        .amount(amount)
                        .status(WalletTransactionStatus.COMPLETED)
                        .description(description)
                        .appointmentId(appointmentId)
                        .build()
        );
    }

    public BigDecimal calculateTopupCreditedAmount(BigDecimal paidAmount) {
        if (paidAmount == null || paidAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("So tien nap phai lon hon 0");
        }
        return paidAmount.multiply(WALLET_TOPUP_CREDIT_RATE);

//        BigDecimal creditedAmount = paidAmount.subtract(WALLET_TOPUP_FIXED_FEE);
//        if (creditedAmount.compareTo(BigDecimal.ZERO) <= 0) {
//            throw new IllegalArgumentException("So tien nhan vao vi phai lon hon 0 sau khi tru phi");
//        }
//
//        return creditedAmount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    @Transactional
    public void refund(CustomerProfile customer, BigDecimal amount, String description, Long appointmentId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("So tien hoan phai lon hon 0");
        }

        Wallet wallet = getOrCreateWallet(customer);
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        walletTransactionRepository.save(
                WalletTransaction.builder()
                        .wallet(wallet)
                        .type(WalletTransactionType.REFUND)
                        .amount(amount)
                        .status(WalletTransactionStatus.COMPLETED)
                        .description(description)
                        .appointmentId(appointmentId)
                        .build()
        );

        try {
            notificationService.notifyWalletRefund(customer, amount);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Transactional
    public void pay(CustomerProfile customer, BigDecimal amount, String description, Long appointmentId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("So tien thanh toan phai lon hon 0");
        }

        Wallet wallet = getOrCreateWallet(customer);
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("So du vi khong du de thanh toan");
        }
        if (getAvailableBalance(customer).compareTo(amount) < 0) {
            throw new IllegalArgumentException("So du kha dung khong du de thanh toan");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);

        walletTransactionRepository.save(
                WalletTransaction.builder()
                        .wallet(wallet)
                        .type(WalletTransactionType.PAYMENT)
                        .amount(amount)
                        .status(WalletTransactionStatus.COMPLETED)
                        .description(description)
                        .appointmentId(appointmentId)
                        .build()
        );
    }

    public BigDecimal getBalance(CustomerProfile customer) {
        return getOrCreateWallet(customer).getBalance();
    }

    public BigDecimal getPendingWithdrawalAmount(CustomerProfile customer) {
        if (customer == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        Wallet wallet = getOrCreateWallet(customer);
        BigDecimal pendingAmount = walletTransactionRepository.sumAmountByWalletAndTypeAndStatus(
                wallet.getId(),
                WalletTransactionType.WITHDRAWAL,
                WalletTransactionStatus.PENDING);
        return (pendingAmount == null ? BigDecimal.ZERO : pendingAmount).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getAvailableBalance(CustomerProfile customer) {
        BigDecimal balance = getBalance(customer).setScale(2, RoundingMode.HALF_UP);
        BigDecimal pendingWithdrawal = getPendingWithdrawalAmount(customer);
        return balance.subtract(pendingWithdrawal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public WalletTransaction createWithdrawalRequest(Long userId, BigDecimal amount, String description) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("So tien rut phai lon hon 0");
        }

        CustomerProfile customer = customerProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay khach hang"));
        Wallet wallet = getOrCreateWallet(customer);
        BigDecimal normalizedAmount = amount.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        if (getAvailableBalance(customer).compareTo(normalizedAmount) < 0) {
            throw new IllegalArgumentException("So du kha dung khong du de tao yeu cau rut tien");
        }
        validateDailyWithdrawalLimit(wallet, normalizedAmount);

        String normalizedDescription = description == null ? "" : description.trim();
        if (normalizedDescription.isBlank()) {
            normalizedDescription = "Khach hang tao yeu cau rut tien mat";
        }

        return walletTransactionRepository.save(
                WalletTransaction.builder()
                        .wallet(wallet)
                        .type(WalletTransactionType.WITHDRAWAL)
                        .amount(normalizedAmount)
                        .status(WalletTransactionStatus.PENDING)
                        .description(normalizedDescription)
                        .appointmentId(null)
                        .build()
        );
    }

    @Transactional(readOnly = true)
    public List<WalletTransaction> getPendingWithdrawalRequests() {
        return walletTransactionRepository.findByTypeAndStatusOrderByCreatedAtAsc(
                WalletTransactionType.WITHDRAWAL,
                WalletTransactionStatus.PENDING);
    }

    @Transactional
    public WalletTransaction approveWithdrawalRequest(Long transactionId) {
        WalletTransaction transaction = walletTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay yeu cau rut tien"));

        if (transaction.getType() != WalletTransactionType.WITHDRAWAL) {
            throw new IllegalArgumentException("Giao dich nay khong phai yeu cau rut tien");
        }
        if (transaction.getStatus() != WalletTransactionStatus.PENDING) {
            throw new IllegalArgumentException("Yeu cau rut tien nay da duoc xu ly");
        }

        Wallet wallet = transaction.getWallet();
        if (wallet == null) {
            throw new IllegalArgumentException("Khong tim thay vi cua khach hang");
        }

        BigDecimal amount = transaction.getAmount() == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : transaction.getAmount().max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("So du vi hien tai khong du de duyet yeu cau rut tien");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);

        transaction.setStatus(WalletTransactionStatus.COMPLETED);
        if (transaction.getDescription() == null || transaction.getDescription().isBlank()) {
            transaction.setDescription("Da chi tra tien mat cho khach va tru vi");
        }
        return walletTransactionRepository.save(transaction);
    }

    @Transactional
    public WalletTransaction rejectWithdrawalRequest(Long transactionId, String reason) {
        WalletTransaction transaction = walletTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay yeu cau rut tien"));

        if (transaction.getType() != WalletTransactionType.WITHDRAWAL) {
            throw new IllegalArgumentException("Giao dich nay khong phai yeu cau rut tien");
        }
        if (transaction.getStatus() != WalletTransactionStatus.PENDING) {
            throw new IllegalArgumentException("Yeu cau rut tien nay da duoc xu ly");
        }

        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isBlank()) {
            throw new IllegalArgumentException("Vui long nhap ly do tu choi");
        }

        transaction.setStatus(WalletTransactionStatus.CANCELLED);
        String currentDescription = transaction.getDescription() == null ? "" : transaction.getDescription().trim();
        transaction.setDescription(
                currentDescription.isBlank()
                        ? "Yeu cau rut tien bi tu choi. Ly do: " + normalizedReason
                        : currentDescription + " | Ly do tu choi: " + normalizedReason);
        return walletTransactionRepository.save(transaction);
    }

    public Optional<Wallet> getWalletByUserId(Long userId) {
        return walletRepository.findByCustomer_User_Id(userId);
    }

    private void validateDailyWithdrawalLimit(Wallet wallet, BigDecimal requestedAmount) {
        if (wallet == null || requestedAmount == null) {
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDateTime from = today.atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay().minusNanos(1);
        BigDecimal withdrawnToday = walletTransactionRepository
                .sumAmountByWalletAndTypeAndStatusesAndCreatedAtBetween(
                        wallet.getId(),
                        WalletTransactionType.WITHDRAWAL,
                        Set.of(WalletTransactionStatus.PENDING, WalletTransactionStatus.COMPLETED),
                        from,
                        to);

        BigDecimal normalizedToday = (withdrawnToday == null ? BigDecimal.ZERO : withdrawnToday)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal projectedTotal = normalizedToday.add(requestedAmount).setScale(2, RoundingMode.HALF_UP);

        if (projectedTotal.compareTo(DAILY_WITHDRAWAL_LIMIT) > 0) {
            throw new IllegalArgumentException("Han muc rut tien toi da moi ngay la 5.000.000 VND");
        }
    }

    public List<WalletTransaction> getTransactionHistory(Wallet wallet) {
        return walletTransactionRepository.findByWallet_IdOrderByCreatedAtDesc(wallet.getId());
    }

    public List<WalletTransaction> getTransactionsByAppointment(Long appointmentId) {
        return walletTransactionRepository.findByAppointmentId(appointmentId);
    }
}
