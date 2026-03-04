package com.dentalclinic.model.wallet;

public enum WalletTransactionType {
    DEPOSIT,      // Nạp tiền từ thanh toán cọc
    REFUND,       // Hoàn tiền khi hủy lịch
    PAYMENT,      // Thanh toán dịch vụ từ ví
    ADJUSTMENT    // Điều chỉnh số dư (admin)
}
