package com.dentalclinic.service.wallet;

import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.wallet.Wallet;
import com.dentalclinic.model.wallet.WalletTransaction;
import com.dentalclinic.model.wallet.WalletTransactionStatus;
import com.dentalclinic.model.wallet.WalletTransactionType;
import com.dentalclinic.repository.WalletRepository;
import com.dentalclinic.repository.WalletTransactionRepository;
import com.dentalclinic.service.notification.NotificationService;
//import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
//@RequiredArgsConstructor
public class WalletService {
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
                .orElseGet(() -> {
                    Wallet wallet = Wallet.builder()
                            .customer(customer)
                            .balance(BigDecimal.ZERO)
                            .build();
                    return walletRepository.save(wallet);
                });
    }

    @Transactional
    public void deposit(CustomerProfile customer, BigDecimal amount, String description, Long appointmentId) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền phải lớn hơn 0");
        }

        Wallet wallet = getOrCreateWallet(customer);
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        WalletTransaction transaction = WalletTransaction.builder()
                .wallet(wallet)
                .type(WalletTransactionType.DEPOSIT)
                .amount(amount)
                .status(WalletTransactionStatus.COMPLETED)
                .description(description)
                .appointmentId(appointmentId)
                .build();
        walletTransactionRepository.save(transaction);
    }

    @Transactional
    public void refund(CustomerProfile customer, BigDecimal amount, String description, Long appointmentId) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền ho� n phải lớn hơn 0");
        }

        Wallet wallet = getOrCreateWallet(customer);
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        WalletTransaction transaction = WalletTransaction.builder()
                .wallet(wallet)
                .type(WalletTransactionType.REFUND)
                .amount(amount)
                .status(WalletTransactionStatus.COMPLETED)
                .description(description)
                .appointmentId(appointmentId)
                .build();
        walletTransactionRepository.save(transaction);

        // Gửi notification ho� n tiền
        try {
            notificationService.notifyWalletRefund(customer, amount);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Transactional
    public void pay(CustomerProfile customer, BigDecimal amount, String description, Long appointmentId) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("So tien thanh toan phai lon hon 0");
        }

        Wallet wallet = getOrCreateWallet(customer);
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("So du vi khong du de thanh toan");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);

        WalletTransaction transaction = WalletTransaction.builder()
                .wallet(wallet)
                .type(WalletTransactionType.PAYMENT)
                .amount(amount)
                .status(WalletTransactionStatus.COMPLETED)
                .description(description)
                .appointmentId(appointmentId)
                .build();
        walletTransactionRepository.save(transaction);
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

