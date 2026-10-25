package com.tlcn.payment_service.enums;

public enum PaymentType {
    CUSTOMER_ORDER, // Thanh toán cho đơn hàng của khách
    VENDOR_FEE,     // Vendor thanh toán phí cho sàn
    WALLET_TOPUP    // Nạp tiền vào ví khách hàng qua VNPay
}