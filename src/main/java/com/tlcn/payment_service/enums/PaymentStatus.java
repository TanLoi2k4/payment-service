package com.tlcn.payment_service.enums;

public enum PaymentStatus {
    PENDING,    // Chờ thanh toán
    COMPLETED,  // Thanh toán thành công
    FAILED,     // Thanh toán thất bại
    CANCELLED,  // Đã hủy trước khi thanh toán
    REFUNDED,   // Hoàn tiền toàn bộ
    PARTIALLY_REFUNDED // Hoàn tiền một phần
}
