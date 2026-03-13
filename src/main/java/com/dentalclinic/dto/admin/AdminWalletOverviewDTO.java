package com.dentalclinic.dto.admin;

import java.math.BigDecimal;

public class AdminWalletOverviewDTO {
    private final BigDecimal totalDeposits;
    private final BigDecimal totalWithdrawals;
    private final BigDecimal totalPayments;
    private final BigDecimal totalRefunds;
    private final BigDecimal totalWalletBalance;
    private final long transactionCount;

    public AdminWalletOverviewDTO(BigDecimal totalDeposits,
                                  BigDecimal totalWithdrawals,
                                  BigDecimal totalPayments,
                                  BigDecimal totalRefunds,
                                  BigDecimal totalWalletBalance,
                                  long transactionCount) {
        this.totalDeposits = totalDeposits;
        this.totalWithdrawals = totalWithdrawals;
        this.totalPayments = totalPayments;
        this.totalRefunds = totalRefunds;
        this.totalWalletBalance = totalWalletBalance;
        this.transactionCount = transactionCount;
    }

    public BigDecimal getTotalDeposits() {
        return totalDeposits;
    }

    public BigDecimal getTotalWithdrawals() {
        return totalWithdrawals;
    }

    public BigDecimal getTotalPayments() {
        return totalPayments;
    }

    public BigDecimal getTotalRefunds() {
        return totalRefunds;
    }

    public BigDecimal getTotalWalletBalance() {
        return totalWalletBalance;
    }

    public long getTransactionCount() {
        return transactionCount;
    }
}
