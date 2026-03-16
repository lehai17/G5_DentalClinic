package com.dentalclinic.service.wallet;

import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.user.User;
import com.dentalclinic.model.wallet.DemoBankAccount;
import com.dentalclinic.model.wallet.Wallet;
import com.dentalclinic.model.wallet.WalletTransaction;
import com.dentalclinic.model.wallet.WalletTransactionStatus;
import com.dentalclinic.model.wallet.WalletTransactionType;
import com.dentalclinic.repository.CustomerProfileRepository;
import com.dentalclinic.repository.DemoBankAccountRepository;
import com.dentalclinic.repository.WalletRepository;
import com.dentalclinic.repository.WalletTransactionRepository;
import com.dentalclinic.service.mail.EmailService;
import com.dentalclinic.service.notification.NotificationService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
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

    public static final BigDecimal MIN_WITHDRAW_AMOUNT = new BigDecimal("10000");
    public static final BigDecimal PIN_REQUIRED_THRESHOLD = new BigDecimal("1000000");
    public static final BigDecimal DAILY_WITHDRAW_LIMIT = new BigDecimal("10000000");
    private static final int MAX_PIN_ATTEMPTS = 5;
    private static final int OTP_EXPIRE_MINUTES = 10;
    private static final int PIN_RESET_WINDOW_MINUTES = 10;
    private static final int WALLET_LOCK_MINUTES = 60;

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final DemoBankAccountRepository demoBankAccountRepository;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final TransactionTemplate requiresNewTransactionTemplate;
    private final SecureRandom random = new SecureRandom();

    public WalletService(WalletRepository walletRepository,
                         WalletTransactionRepository walletTransactionRepository,
                         CustomerProfileRepository customerProfileRepository,
                         DemoBankAccountRepository demoBankAccountRepository,
                         NotificationService notificationService,
                         PasswordEncoder passwordEncoder,
                         EmailService emailService,
                         PlatformTransactionManager transactionManager) {
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.demoBankAccountRepository = demoBankAccountRepository;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
            throw new IllegalArgumentException("So tien phai lon hon 0");
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
            throw new IllegalArgumentException("So tien hoan phai lon hon 0");
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

    @Transactional(noRollbackFor = {WalletPinRequiredException.class, WalletLockedException.class})
    public WithdrawResult withdraw(CustomerProfile customer,
                                   BigDecimal amount,
                                   String bankName,
                                   String bankAccountNo,
                                   String pinCode) {
        Wallet wallet = getOrCreateWallet(customer);
        validateWithdrawPreconditions(wallet, amount);
        verifyPinIfNeeded(wallet, amount, pinCode);

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("So du vi khong du de rut tien");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);

        String safeBankName = bankName == null ? "" : bankName.trim();
        String safeAccountNo = bankAccountNo == null ? "" : bankAccountNo.trim();
        DemoBankAccount destinationAccount = getOrCreateDemoBankAccount(safeBankName, safeAccountNo);
        destinationAccount.setBalance(destinationAccount.getBalance().add(amount));
        destinationAccount = demoBankAccountRepository.save(destinationAccount);

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
        return getOrCreateDemoBankAccount(bankName.trim(), accountNo.trim());
    }

    @Transactional
    public DemoBankAccount savePersonalWithdrawAccount(CustomerProfile customer,
                                                       String bankName,
                                                       String accountNo) {
        validateBankLookupInput(bankName, accountNo);
        customer.setWithdrawBankName(bankName.trim());
        customer.setWithdrawAccountNo(accountNo.trim());
        customerProfileRepository.save(customer);
        return getOrCreateDemoBankAccount(customer.getWithdrawBankName(), customer.getWithdrawAccountNo());
    }

    public DemoBankAccount getPersonalWithdrawAccount(CustomerProfile customer) {
        String bankName = customer.getWithdrawBankName();
        String accountNo = customer.getWithdrawAccountNo();
        if (bankName == null || bankName.isBlank() || accountNo == null || accountNo.isBlank()) {
            return null;
        }
        return getOrCreateDemoBankAccount(bankName.trim(), accountNo.trim());
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

    @Transactional
    public WalletSecurityState getSecurityState(CustomerProfile customer) {
        Wallet wallet = getOrCreateWallet(customer);
        BigDecimal withdrawnToday = getWithdrawnToday(wallet);
        LocalDateTime now = LocalDateTime.now();
        boolean locked = wallet.getPinLockedUntil() != null && wallet.getPinLockedUntil().isAfter(now);
        return new WalletSecurityState(
                wallet.getPinCode() != null && !wallet.getPinCode().isBlank(),
                locked,
                wallet.getPinLockedUntil(),
                DAILY_WITHDRAW_LIMIT,
                withdrawnToday,
                DAILY_WITHDRAW_LIMIT.subtract(withdrawnToday).max(BigDecimal.ZERO),
                PIN_REQUIRED_THRESHOLD
        );
    }

    @Transactional
    public void setupPin(CustomerProfile customer, String pin, String confirmPin) {
        Wallet wallet = getOrCreateWallet(customer);
        validatePinFormat(pin, confirmPin);

        boolean hasExistingPin = wallet.getPinCode() != null && !wallet.getPinCode().isBlank();
        if (hasExistingPin && !isPinResetVerified(wallet)) {
            throw new IllegalArgumentException("PIN da duoc thiet lap. Vui long dung luong quen PIN neu can dat lai.");
        }

        wallet.setPinCode(passwordEncoder.encode(pin));
        wallet.setPinFailedAttempts(0);
        wallet.setPinLockedUntil(null);
        wallet.setPinResetOtpHash(null);
        wallet.setPinResetOtpExpiresAt(null);
        wallet.setPinResetVerifiedUntil(null);
        walletRepository.save(wallet);
    }

    @Transactional
    public void requestPinResetOtp(CustomerProfile customer) {
        Wallet wallet = getOrCreateWallet(customer);
        User user = customer.getUser();
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalArgumentException("Khong tim thay email de gui OTP.");
        }

        String otp = generateOtp();
        wallet.setPinResetOtpHash(passwordEncoder.encode(otp));
        wallet.setPinResetOtpExpiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRE_MINUTES));
        wallet.setPinResetVerifiedUntil(null);
        walletRepository.save(wallet);

        emailService.sendWalletPinOtp(user, otp);
    }

    @Transactional
    public void verifyPinResetOtp(CustomerProfile customer, String otp) {
        Wallet wallet = getOrCreateWallet(customer);
        if (wallet.getPinResetOtpHash() == null || wallet.getPinResetOtpExpiresAt() == null) {
            throw new IllegalArgumentException("Ban chua yeu cau OTP dat lai PIN.");
        }
        if (wallet.getPinResetOtpExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("OTP da het han.");
        }
        if (!passwordEncoder.matches(otp, wallet.getPinResetOtpHash())) {
            throw new IllegalArgumentException("OTP khong hop le.");
        }

        wallet.setPinResetVerifiedUntil(LocalDateTime.now().plusMinutes(PIN_RESET_WINDOW_MINUTES));
        wallet.setPinResetOtpHash(null);
        wallet.setPinResetOtpExpiresAt(null);
        walletRepository.save(wallet);
    }

    private void validateWithdrawPreconditions(Wallet wallet, BigDecimal amount) {
        if (amount == null || amount.compareTo(MIN_WITHDRAW_AMOUNT) < 0) {
            throw new IllegalArgumentException("So tien rut toi thieu la 10.000 VND.");
        }

        ensureWalletNotLocked(wallet);

        BigDecimal withdrawnToday = getWithdrawnToday(wallet);
        BigDecimal nextTotal = withdrawnToday.add(amount);
        if (nextTotal.compareTo(DAILY_WITHDRAW_LIMIT) > 0) {
            BigDecimal remaining = DAILY_WITHDRAW_LIMIT.subtract(withdrawnToday).max(BigDecimal.ZERO);
            throw new IllegalArgumentException("Ban chi duoc rut toi da 10.000.000 VND moi ngay. Han muc con lai hom nay: " + remaining.toPlainString() + " VND.");
        }
    }

    private void verifyPinIfNeeded(Wallet wallet, BigDecimal amount, String pinCode) {
        if (amount.compareTo(PIN_REQUIRED_THRESHOLD) < 0) {
            return;
        }

        if (wallet.getPinCode() == null || wallet.getPinCode().isBlank()) {
            throw new WalletPinRequiredException("PIN_SETUP_REQUIRED", "Rut tu 1.000.000 VND can thiet lap PIN cho vi.");
        }

        ensureWalletNotLocked(wallet);

        if (pinCode == null || pinCode.isBlank()) {
            throw new WalletPinRequiredException("PIN_REQUIRED", "Rut tu 1.000.000 VND can nhap PIN cua vi.");
        }
        if (!pinCode.matches("\\d{6}")) {
            throw new IllegalArgumentException("PIN phai gom dung 6 chu so.");
        }

        if (passwordEncoder.matches(pinCode, wallet.getPinCode())) {
            wallet.setPinFailedAttempts(0);
            wallet.setPinLockedUntil(null);
            walletRepository.save(wallet);
            return;
        }

        int failedAttempts = wallet.getPinFailedAttempts() + 1;
        if (failedAttempts >= MAX_PIN_ATTEMPTS) {
            LocalDateTime lockedUntil = LocalDateTime.now().plusMinutes(WALLET_LOCK_MINUTES);
            savePinFailureState(wallet.getId(), 0, lockedUntil);
            throw new WalletLockedException("WALLET_LOCKED", "Ban da nhap sai PIN qua 5 lan. Chuc nang vi bi khoa trong 1 gio.", lockedUntil);
        }

        savePinFailureState(wallet.getId(), failedAttempts, null);
        throw new WalletPinRequiredException(
                "PIN_INVALID",
                "PIN khong dung. Ban con " + (MAX_PIN_ATTEMPTS - failedAttempts) + " lan thu."
        );
    }

    private void ensureWalletNotLocked(Wallet wallet) {
        if (wallet.getPinLockedUntil() != null && wallet.getPinLockedUntil().isAfter(LocalDateTime.now())) {
            throw new WalletLockedException(
                    "WALLET_LOCKED",
                    "Chuc nang vi dang bi khoa tam thoi den " + wallet.getPinLockedUntil() + ".",
                    wallet.getPinLockedUntil()
            );
        }
        if (wallet.getPinLockedUntil() != null && !wallet.getPinLockedUntil().isAfter(LocalDateTime.now())) {
            wallet.setPinLockedUntil(null);
            wallet.setPinFailedAttempts(0);
            walletRepository.save(wallet);
        }
    }

    private boolean isPinResetVerified(Wallet wallet) {
        return wallet.getPinResetVerifiedUntil() != null
                && wallet.getPinResetVerifiedUntil().isAfter(LocalDateTime.now());
    }

    private BigDecimal getWithdrawnToday(Wallet wallet) {
        LocalDate today = LocalDate.now();
        LocalDateTime from = today.atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay().minusNanos(1);
        BigDecimal result = walletTransactionRepository.sumAmountByWalletAndTypeAndCreatedAtBetween(
                wallet.getId(),
                WalletTransactionType.WITHDRAW,
                from,
                to
        );
        return result == null ? BigDecimal.ZERO : result;
    }

    private void savePinFailureState(Long walletId, int failedAttempts, LocalDateTime lockedUntil) {
        requiresNewTransactionTemplate.executeWithoutResult(status -> {
            Wallet latestWallet = walletRepository.findById(walletId)
                    .orElseThrow(() -> new IllegalArgumentException("Khong tim thay vi."));
            latestWallet.setPinFailedAttempts(failedAttempts);
            latestWallet.setPinLockedUntil(lockedUntil);
            walletRepository.save(latestWallet);
        });
    }

    private DemoBankAccount getOrCreateDemoBankAccount(String bankName, String accountNo) {
        return demoBankAccountRepository.findByBankNameAndAccountNo(bankName, accountNo)
                .orElseGet(() -> {
                    DemoBankAccount account = new DemoBankAccount();
                    account.setBankName(bankName);
                    account.setAccountNo(accountNo);
                    account.setAccountHolder(generateAccountHolder(bankName, accountNo));
                    account.setBalance(BigDecimal.ZERO);
                    return demoBankAccountRepository.save(account);
                });
    }

    private void validateBankLookupInput(String bankName, String accountNo) {
        if (bankName == null || bankName.isBlank()) {
            throw new IllegalArgumentException("Vui long chon ngan hang.");
        }
        if (accountNo == null || !accountNo.matches("\\d{10}")) {
            throw new IllegalArgumentException("So tai khoan phai gom dung 10 chu so.");
        }
    }

    private void validatePinFormat(String pin, String confirmPin) {
        if (pin == null || !pin.matches("\\d{6}")) {
            throw new IllegalArgumentException("PIN phai gom dung 6 chu so.");
        }
        if (!pin.equals(confirmPin)) {
            throw new IllegalArgumentException("PIN xac nhan khong khop.");
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

    private String generateOtp() {
        int n = random.nextInt(900000) + 100000;
        return String.valueOf(n);
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

    public record WalletSecurityState(boolean hasPin,
                                      boolean walletLocked,
                                      LocalDateTime pinLockedUntil,
                                      BigDecimal dailyWithdrawLimit,
                                      BigDecimal withdrawnToday,
                                      BigDecimal remainingDailyLimit,
                                      BigDecimal pinRequiredThreshold) {
    }

    public static class WalletPinRequiredException extends IllegalArgumentException {
        private final String code;

        public WalletPinRequiredException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    public static class WalletLockedException extends IllegalArgumentException {
        private final String code;
        private final LocalDateTime lockedUntil;

        public WalletLockedException(String code, String message, LocalDateTime lockedUntil) {
            super(message);
            this.code = code;
            this.lockedUntil = lockedUntil;
        }

        public String getCode() {
            return code;
        }

        public LocalDateTime getLockedUntil() {
            return lockedUntil;
        }
    }
}
