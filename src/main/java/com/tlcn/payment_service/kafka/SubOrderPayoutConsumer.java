package com.tlcn.payment_service.kafka;

import com.tlcn.payment_service.dto.kafka.SubOrderPayoutRequested;
import com.tlcn.payment_service.enums.PaymentStatus;
import com.tlcn.payment_service.model.PlatformPayableToVendor;
import com.tlcn.payment_service.model.VendorPayableToPlatform;
import com.tlcn.payment_service.repository.PlatformPayableToVendorRepository;
import com.tlcn.payment_service.repository.VendorPayableToPlatformRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubOrderPayoutConsumer {

    private static final String IDEMPOTENCY_KEY_PREFIX = "kafka:suborder-payout:idempotency:";

    private final PlatformPayableToVendorRepository platformPayableToVendorRepository;
    private final VendorPayableToPlatformRepository vendorPayableToPlatformRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${kafka.idempotency.ttl-hours:24}")
    private long idempotencyTtlHours;

    @KafkaListener(
        topics = "${spring.kafka.topics.suborder-payout-requested:suborder-payout-requested}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeSubOrderPayoutRequested(
        SubOrderPayoutRequested event,
        @Header(name = "messageId", required = false) String messageId,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        Acknowledgment ack
    ) {
        if (event == null || event.getOrderCode() == null || event.getOrderCode().isBlank() || event.getSubOrderId() == null) {
            log.warn("Ignore invalid suborder-payout-requested event: messageId={} topic={} partition={} offset={}", messageId, topic, partition, offset);
            ack.acknowledge();
            return;
        }

        String idempotencyKey = buildIdempotencyKey(event.getEventId(), event.getOrderCode(), event.getSubOrderId());
        Boolean claimed = redisTemplate.opsForValue().setIfAbsent(
            idempotencyKey,
            "processing",
            Math.max(1L, idempotencyTtlHours),
            TimeUnit.HOURS
        );
        if (!Boolean.TRUE.equals(claimed)) {
            log.info("Skip duplicate suborder-payout-requested: orderCode={} subOrderId={} messageId={}", event.getOrderCode(), event.getSubOrderId(), messageId);
            ack.acknowledge();
            return;
        }

        try {
            log.info("Processing suborder-payout-requested: orderCode={} subOrderId={} platformPayableToVendor={} vendorPayableToPlatform={} paymentMethod={} topic={} partition={} offset={}",
                event.getOrderCode(),
                event.getSubOrderId(),
                event.getPlatformPayableToVendor(),
                event.getVendorPayableToPlatform(),
                event.getPaymentMethod(),
                topic,
                partition,
                offset
            );

            persistPayables(event);
            redisTemplate.opsForValue().set(
                idempotencyKey,
                "processed",
                Math.max(1L, idempotencyTtlHours),
                TimeUnit.HOURS
            );
            ack.acknowledge();
        } catch (DataIntegrityViolationException duplicate) {
            log.info(
                "Payables already exist for orderCode={} subOrderId={}, treating as idempotent success",
                event.getOrderCode(),
                event.getSubOrderId()
            );
            ack.acknowledge();
        } catch (Exception e) {
            redisTemplate.delete(idempotencyKey);
            log.error("Failed to process suborder-payout-requested: orderCode={} subOrderId={} messageId={} error={}",
                event.getOrderCode(),
                event.getSubOrderId(),
                messageId,
                e.getMessage(),
                e
            );
            throw e;
        }
    }

    private void persistPayables(SubOrderPayoutRequested event) {
        if (event.getPlatformPayableToVendor() != null && event.getPlatformPayableToVendor().signum() > 0
            && !platformPayableToVendorRepository.existsByOrderCodeAndSubOrderId(event.getOrderCode(), event.getSubOrderId())) {
            platformPayableToVendorRepository.save(
                PlatformPayableToVendor.builder()
                    .orderCode(event.getOrderCode())
                    .orderId(event.getOrderId())
                    .subOrderId(event.getSubOrderId())
                    .vendorKeycloakId(event.getVendorKeycloakId())
                    .amount(event.getPlatformPayableToVendor())
                    .status(PaymentStatus.PENDING)
                    .build()
            );
        }

        if (event.getVendorPayableToPlatform() != null && event.getVendorPayableToPlatform().signum() > 0
            && !vendorPayableToPlatformRepository.existsByOrderCodeAndSubOrderId(event.getOrderCode(), event.getSubOrderId())) {
            vendorPayableToPlatformRepository.save(
                VendorPayableToPlatform.builder()
                    .orderCode(event.getOrderCode())
                    .orderId(event.getOrderId())
                    .subOrderId(event.getSubOrderId())
                    .vendorKeycloakId(event.getVendorKeycloakId())
                    .amount(event.getVendorPayableToPlatform())
                    .commissionRate(calculateCommissionRate(event))
                    .status(PaymentStatus.PENDING)
                    .build()
            );
        }
    }

    private String buildIdempotencyKey(String eventId, String orderCode, Long subOrderId) {
        if (eventId != null && !eventId.isBlank()) {
            return IDEMPOTENCY_KEY_PREFIX + eventId;
        }
        return IDEMPOTENCY_KEY_PREFIX + orderCode + ":" + subOrderId;
    }

    private java.math.BigDecimal calculateCommissionRate(SubOrderPayoutRequested event) {
        if (event.getGrossAmountBeforeDiscount() == null || event.getGrossAmountBeforeDiscount().signum() == 0) {
            return java.math.BigDecimal.ZERO;
        }
        if (event.getCommissionFee() == null || event.getCommissionFee().signum() == 0) {
            return java.math.BigDecimal.ZERO;
        }
        return event.getCommissionFee()
            .divide(event.getGrossAmountBeforeDiscount(), 6, java.math.RoundingMode.HALF_UP);
    }
}
