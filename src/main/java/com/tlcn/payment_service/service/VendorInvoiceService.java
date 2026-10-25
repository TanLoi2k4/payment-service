package com.tlcn.payment_service.service;

import com.tlcn.payment_service.dto.CreatePaymentRequest;
import com.tlcn.payment_service.dto.CreatePaymentResponse;
import com.tlcn.payment_service.dto.VendorInvoiceResponse;
import com.tlcn.payment_service.enums.InvoiceStatus;
import com.tlcn.payment_service.enums.PaymentStatus;
import com.tlcn.payment_service.enums.PaymentMethod;
import com.tlcn.payment_service.enums.PaymentType;
import com.tlcn.payment_service.exception.BusinessException;
import com.tlcn.payment_service.exception.ForbiddenException;
import com.tlcn.payment_service.exception.ResourceNotFoundException;
import com.tlcn.payment_service.model.Payment;
import com.tlcn.payment_service.model.VendorInvoice;
import com.tlcn.payment_service.model.VendorPayableToPlatform;
import com.tlcn.payment_service.repository.VendorInvoiceRepository;
import com.tlcn.payment_service.repository.VendorPayableToPlatformRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VendorInvoiceService {

    private final VendorPayableToPlatformRepository vendorPayableToPlatformRepository;
    private final VendorInvoiceRepository vendorInvoiceRepository;
    private final PaymentService paymentService;

    // Chạy vào 0h sáng thứ 2 hàng tuần
    @Scheduled(cron = "0 0 0 * * MON")
    @Transactional
    public void createWeeklyInvoices() {
        log.info("Starting weekly vendor invoice creation job.");
        // Lấy tất cả các khoản phí chưa được thanh toán và chưa được gắn vào hóa đơn nào
        List<VendorPayableToPlatform> unpaidFees = vendorPayableToPlatformRepository.findByStatusAndVendorInvoiceIsNull(PaymentStatus.PENDING);

        // Gom các khoản phí theo từng vendor
        Map<String, List<VendorPayableToPlatform>> feesByVendor = unpaidFees.stream()
                .collect(Collectors.groupingBy(VendorPayableToPlatform::getVendorKeycloakId));

        for (Map.Entry<String, List<VendorPayableToPlatform>> entry : feesByVendor.entrySet()) {
            String vendorId = entry.getKey();
            List<VendorPayableToPlatform> vendorFees = entry.getValue();

            BigDecimal totalAmount = vendorFees.stream()
                    .map(VendorPayableToPlatform::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalAmount.compareTo(BigDecimal.ZERO) > 0) {
                String invoiceCode = "VINV-" + vendorId.substring(0, 5) + "-" + System.currentTimeMillis();
                VendorInvoice invoice = VendorInvoice.builder()
                        .invoiceCode(invoiceCode)
                        .vendorKeycloakId(vendorId)
                        .totalAmount(totalAmount)
                        .status(InvoiceStatus.PENDING)
                        .dueDate(Instant.now().plus(7, ChronoUnit.DAYS)) // Hạn trả trong 7 ngày
                        .build();

                // Liên kết các khoản phí với hóa đơn này
                vendorFees.forEach(fee -> fee.setVendorInvoice(invoice));
                invoice.setPayableItems(vendorFees);

                vendorInvoiceRepository.save(invoice);
                log.info("Created invoice {} for vendor {} with amount {}", invoiceCode, vendorId, totalAmount);
            } else {
                log.info("Skipping invoice creation for vendor {} because total amount is {}", vendorId, totalAmount);
            }
        }
    }

    @Transactional
    public CreatePaymentResponse createPaymentForInvoice(String vendorKeycloakId, Long invoiceId) throws Exception {
        VendorInvoice invoice = vendorInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        if (!Objects.equals(invoice.getVendorKeycloakId(), vendorKeycloakId)) {
            throw new ForbiddenException("You are not authorized to pay this invoice.", "INVOICE_ACCESS_DENIED");
        }
        if (invoice.getStatus() != InvoiceStatus.PENDING && invoice.getStatus() != InvoiceStatus.OVERDUE) {
            throw new BusinessException("Cannot pay for this invoice.", HttpStatus.CONFLICT, "INVOICE_NOT_PAYABLE");
        }
 
        // Nếu đã có payment đang PENDING, trả về link cũ
        if (invoice.getPayment() != null && invoice.getPayment().getStatus() == com.tlcn.payment_service.enums.PaymentStatus.PENDING) {
            log.info("Returning existing pending payment for invoice {}", invoice.getInvoiceCode());
            return CreatePaymentResponse.builder()
                    .paymentId(invoice.getPayment().getId())
                    .orderCode(invoice.getPayment().getOrderCode())
                    .status(invoice.getPayment().getStatus())
                    .checkoutUrl(invoice.getPayment().getCheckoutUrl())
                    .build();
        }
 
        // Tái sử dụng luồng tạo payment
        CreatePaymentRequest paymentRequest = CreatePaymentRequest.builder()
                .orderCode(invoice.getInvoiceCode())
                .customerKeycloakId(vendorKeycloakId) // Vendor đóng vai trò là khách hàng
                .amount(invoice.getTotalAmount())
                .paymentMethod(PaymentMethod.VNPAY)
                .paymentType(PaymentType.VENDOR_FEE)
                .build();
 
        String idempotencyKey = "invoice-payment:" + invoice.getInvoiceCode();
        CreatePaymentResponse paymentResponse = paymentService.createPayment(paymentRequest, idempotencyKey);
 
        // Liên kết payment vừa tạo với invoice
        Payment payment = paymentService.getPaymentOrThrow(paymentResponse.getPaymentId());
        invoice.setPayment(payment);
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            invoice.setStatus(InvoiceStatus.PAID);
        } else if (payment.getStatus() == PaymentStatus.FAILED || payment.getStatus() == PaymentStatus.CANCELLED) {
            invoice.setStatus(InvoiceStatus.CANCELLED);
        }
        vendorInvoiceRepository.save(invoice);
 
        return paymentResponse;
    }

    @Transactional(readOnly = true)
    public VendorInvoiceResponse getInvoiceForVendor(String vendorKeycloakId, Long invoiceId) {
        VendorInvoice invoice = vendorInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        if (!Objects.equals(invoice.getVendorKeycloakId(), vendorKeycloakId)) {
            throw new ForbiddenException("You are not authorized to view this invoice.", "INVOICE_ACCESS_DENIED");
        }

        return toResponse(invoice);
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void markOverdueInvoices() {
        Instant now = Instant.now();
        List<VendorInvoice> overdueInvoices = vendorInvoiceRepository.findAll().stream()
            .filter(invoice -> invoice.getStatus() == InvoiceStatus.PENDING)
            .filter(invoice -> invoice.getDueDate() != null && invoice.getDueDate().isBefore(now))
            .peek(invoice -> invoice.setStatus(InvoiceStatus.OVERDUE))
            .toList();

        if (!overdueInvoices.isEmpty()) {
            vendorInvoiceRepository.saveAll(overdueInvoices);
        }
    }

    /**
     * Retry job: sync invoices that are still PENDING/OVERDUE while their linked payment is COMPLETED.
     */
    @Scheduled(fixedRate = 300000)
    @Transactional
    public void syncCompletedVendorFeeInvoices() {
        List<VendorInvoice> unsynced = vendorInvoiceRepository.findByStatusInAndPaymentStatus(
            List.of(InvoiceStatus.PENDING, InvoiceStatus.OVERDUE),
            PaymentStatus.COMPLETED
        );
        if (unsynced.isEmpty()) {
            return;
        }

        log.info("Found {} vendor invoices to sync after completed fee payment", unsynced.size());
        for (VendorInvoice invoice : unsynced) {
            try {
                Payment payment = invoice.getPayment();
                if (payment == null) {
                    continue;
                }
                paymentService.markVendorFeeInvoicePaid(payment);
            } catch (Exception e) {
                log.error("Failed to sync vendor invoice {}: {}", invoice.getInvoiceCode(), e.getMessage(), e);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<VendorInvoiceResponse> getInvoicesForVendor(String vendorKeycloakId, InvoiceStatus status) {
        List<VendorInvoice> invoices;
        if (status != null) {
            invoices = vendorInvoiceRepository.findByVendorKeycloakIdAndStatusOrderByDueDateDesc(vendorKeycloakId, status);
        } else {
            invoices = vendorInvoiceRepository.findByVendorKeycloakIdOrderByDueDateDesc(vendorKeycloakId);
        }

        return invoices.stream().map(this::toResponse).toList();
    }

    private VendorInvoiceResponse toResponse(VendorInvoice invoice) {
        return VendorInvoiceResponse.builder()
                .id(invoice.getId())
                .invoiceCode(invoice.getInvoiceCode())
                .vendorKeycloakId(invoice.getVendorKeycloakId())
                .totalAmount(invoice.getTotalAmount())
                .status(invoice.getStatus())
                .dueDate(invoice.getDueDate())
                .paymentId(invoice.getPayment() != null ? invoice.getPayment().getId() : null)
                .paymentStatus(invoice.getPayment() != null ? invoice.getPayment().getStatus() : null)
                .checkoutUrl(invoice.getPayment() != null ? invoice.getPayment().getCheckoutUrl() : null)
                .providerPaymentIntentId(invoice.getPayment() != null ? invoice.getPayment().getProviderPaymentIntentId() : null)
                .build();
    }
}