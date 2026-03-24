package com.dentalclinic.service.wallet;

import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.wallet.Wallet;
import com.dentalclinic.model.wallet.WalletTransaction;
import com.dentalclinic.model.wallet.WalletTransactionStatus;
import com.dentalclinic.model.wallet.WalletTransactionType;
import com.dentalclinic.repository.WalletRepository;
import com.dentalclinic.repository.WalletTransactionRepository;
import com.dentalclinic.service.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class WalletService {
    public static final BigDecimal WALLET_TOPUP_CREDIT_RATE = BigDecimal.ONE;
//    public static final BigDecimal WALLET_TOPUP_CREDIT_RATE = new BigDecimal("0.95");
//    public static final BigDecimal WALLET_TOPUP_FIXED_FEE = BigDecimal.valueOf(5_000L);



    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final NotificationService notificationService;

    public WalletService(WalletRepository walletRepository,
                         WalletTransactionRepository walletTransactionRepository,
                         NotificationService notificationService) {
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.notificationService = notificationService;
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

    public Optional<Wallet> getWalletByUserId(Long userId) {
        return walletRepository.findByCustomer_User_Id(userId);
    }

    public List<WalletTransaction> getTransactionHistory(Wallet wallet) {
        return walletTransactionRepository.findByWallet_IdOrderByCreatedAtDesc(wallet.getId());
    }

    public List<WalletTransaction> getTransactionsByAppointment(Long appointmentId) {
        return walletTransactionRepository.findByAppointmentId(appointmentId);
    }
}
