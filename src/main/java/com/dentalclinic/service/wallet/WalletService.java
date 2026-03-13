package com.dentalclinic.service.wallet;

import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.wallet.DemoBankAccount;
import com.dentalclinic.model.wallet.Wallet;
import com.dentalclinic.model.wallet.WalletTransaction;
import com.dentalclinic.model.wallet.WalletTransactionStatus;
import com.dentalclinic.model.wallet.WalletTransactionType;
import com.dentalclinic.repository.CustomerProfileRepository;
import com.dentalclinic.repository.WalletRepository;
import com.dentalclinic.repository.WalletTransactionRepository;
import com.dentalclinic.service.notification.NotificationService;
//import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.List;
import java.util.Optional;

@Service
//@RequiredArgsConstructor
public class WalletService {
    private static final String[] FIRST_NAMES = {
            "Nguyen", "Tran", "Le", "Pham", "Hoang", "Vo", "Dang", "Bui", "Do", "Ngo"
    };
    private static final String[] MIDDLE_NAMES = {
            "Thanh", "Duc", "Gia", "Quoc", "Minh", "Anh", "Thu", "Bao", "Ngoc", "Hai"
    };
    private static final String[] LAST_NAMES = {
            "An", "Binh", "Chau", "Dung", "Giang", "Ha", "Khanh", "Lam", "Nam", "Phuong"
    };

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final NotificationService notificationService;

    public WalletService(WalletRepository walletRepository,
                         WalletTransactionRepository walletTransactionRepository,
                         CustomerProfileRepository customerProfileRepository,
                         NotificationService notificationService) {
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.customerProfileRepository = customerProfileRepository;
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

    @Transactional
    public WithdrawResult withdraw(CustomerProfile customer,
                                   BigDecimal amount,
                                   String bankName,
                                   String bankAccountNo) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("So tien rut phai lon hon 0");
        }

        Wallet wallet = getOrCreateWallet(customer);
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("So du vi khong du de rut tien");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);

        String safeBankName = bankName == null ? "" : bankName.trim();
        String safeAccountNo = bankAccountNo == null ? "" : bankAccountNo.trim();
        DemoBankAccount destinationAccount = buildDemoBankAccount(safeBankName, safeAccountNo);

        String maskedAccountNo = maskBankAccount(safeAccountNo);
        String description = String.format(
                "Rut tien ve %s - STK %s - Chu TK %s (demo transfer)",
                safeBankName,
                maskedAccountNo,
                destinationAccount.getAccountHolder()
        );

        WalletTransaction transaction = WalletTransaction.builder()
                .wallet(wallet)
                .type(WalletTransactionType.WITHDRAW)
                .amount(amount)
                .status(WalletTransactionStatus.COMPLETED)
                .description(description)
                .appointmentId(null)
                .build();
        walletTransactionRepository.save(transaction);

        return new WithdrawResult(wallet.getBalance(), destinationAccount);
    }

    @Transactional
    public DemoBankAccount resolveDemoBankAccount(String bankName, String accountNo) {
        validateBankLookupInput(bankName, accountNo);
        return buildDemoBankAccount(bankName.trim(), accountNo.trim());
    }

    @Transactional
    public DemoBankAccount savePersonalWithdrawAccount(CustomerProfile customer,
                                                       String bankName,
                                                       String accountNo) {
        validateBankLookupInput(bankName, accountNo);
        customer.setWithdrawBankName(bankName.trim());
        customer.setWithdrawAccountNo(accountNo.trim());
        customerProfileRepository.save(customer);
        return buildDemoBankAccount(customer.getWithdrawBankName(), customer.getWithdrawAccountNo());
    }

    public DemoBankAccount getPersonalWithdrawAccount(CustomerProfile customer) {
        String bankName = customer.getWithdrawBankName();
        String accountNo = customer.getWithdrawAccountNo();
        if (bankName == null || bankName.isBlank() || accountNo == null || accountNo.isBlank()) {
            return null;
        }
        return buildDemoBankAccount(bankName.trim(), accountNo.trim());
    }

    private DemoBankAccount buildDemoBankAccount(String bankName, String accountNo) {
        DemoBankAccount account = new DemoBankAccount();
        account.setBankName(bankName);
        account.setAccountNo(accountNo);
        account.setAccountHolder(generateAccountHolder(bankName, accountNo));
        account.setBalance(BigDecimal.ZERO);
        return account;
    }

    private void validateBankLookupInput(String bankName, String accountNo) {
        if (bankName == null || bankName.isBlank()) {
            throw new IllegalArgumentException("Vui long chon ngan hang.");
        }
        if (accountNo == null || !accountNo.matches("\\d{10}")) {
            throw new IllegalArgumentException("So tai khoan phai gom dung 10 chu so.");
        }
    }

    private String generateAccountHolder(String bankName, String accountNo) {
        int seed = (bankName + "|" + accountNo).hashCode();
        String first = FIRST_NAMES[Math.floorMod(seed, FIRST_NAMES.length)];
        String middle = MIDDLE_NAMES[Math.floorMod(seed / 11, MIDDLE_NAMES.length)];
        String last = LAST_NAMES[Math.floorMod(seed / 97, LAST_NAMES.length)];
        return (first + " " + middle + " " + last).toUpperCase(Locale.ROOT);
    }

    private String maskBankAccount(String accountNo) {
        if (accountNo == null || accountNo.isBlank()) {
            return "N/A";
        }
        if (accountNo.length() <= 4) {
            return accountNo;
        }
        return "*".repeat(accountNo.length() - 4) + accountNo.substring(accountNo.length() - 4);
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

    public static class WithdrawResult {
        private final BigDecimal walletBalance;
        private final DemoBankAccount destinationAccount;

        public WithdrawResult(BigDecimal walletBalance, DemoBankAccount destinationAccount) {
            this.walletBalance = walletBalance;
            this.destinationAccount = destinationAccount;
        }

        public BigDecimal getWalletBalance() {
            return walletBalance;
        }

        public DemoBankAccount getDestinationAccount() {
            return destinationAccount;
        }
    }
}

