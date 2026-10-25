package com.tlcn.payment_service.enums;

public enum PayoutStatus {
    PENDING_APPROVAL, // Chờ admin duyệt
    APPROVED,         // Đã duyệt, chờ xử lý chi trả
    REJECTED,         // Bị từ chối
    PROCESSING,       // Đang xử lý chi trả
    COMPLETED,        // Đã chi trả thành công
    FAILED,           // Chi trả thất bại
    FAILED_RECONCILED // Đã thất bại và đã xử lý (hoàn trả item)
}