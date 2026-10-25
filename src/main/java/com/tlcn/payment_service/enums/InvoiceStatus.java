package com.tlcn.payment_service.enums;

public enum InvoiceStatus {
    PENDING,    // Đang chờ thanh toán
    PAID,       // Đã thanh toán
    OVERDUE,    // Quá hạn
    CANCELLED   // Đã hủy
}