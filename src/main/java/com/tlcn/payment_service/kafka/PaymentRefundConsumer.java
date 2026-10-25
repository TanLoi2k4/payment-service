package com.tlcn.payment_service.kafka;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.tlcn.payment_service.dto.RefundRequest;
import com.tlcn.payment_service.service.RefundService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRefundConsumer {

    private static final String IDEMPOTENCY_KEY_PREFIX = "kafka:refund:idempotency:";

    private final RefundService refundService;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${kafka.idempotency.ttl-hours:24}")
    private long idempotencyTtlHours;

    @KafkaListener(
        topics = "${spring.kafka.topics.payment-refund-request:payment-refund-request}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePaymentRefundRequest(
        RefundRequest request,
        @Header(name = "messageId", required = false) String messageId,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        Acknowledgment ack
    ) {
        try {
            if (request == null || request.getOrderCode() == null || request.getOrderCode().isBlank()) {
                log.warn("Ignore invalid payment-refund-request event: messageId={} topic={} partition={} offset={}", messageId, topic, partition, offset);
                ack.acknowledge();
                return;
            }

            String idempotencyKey = buildIdempotencyKey(messageId, request.getOrderCode());
            if (isAlreadyProcessed(idempotencyKey)) {
                log.info("Skip duplicate payment-refund-request: orderCode={} messageId={}", request.getOrderCode(), messageId);
                ack.acknowledge();
                return;
            }

            log.info("Received payment-refund-request: orderCode={} amount={} currency={} topic={} partition={} offset={}",
                request.getOrderCode(),
                request.getAmount(),
                request.getCurrency(),
                topic,
                partition,
                offset
            );

            refundService.requestRefund(request, idempotencyKey);
            markAsProcessed(idempotencyKey);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process payment-refund-request: orderCode={} messageId={} error={}",
                request == null ? null : request.getOrderCode(),
                messageId,
                e.getMessage(),
                e
            );
            throw e;
        }
    }

    private String buildIdempotencyKey(String messageId, String orderCode) {
        if (messageId != null && !messageId.isBlank()) {
            return IDEMPOTENCY_KEY_PREFIX + messageId;
        }
        return IDEMPOTENCY_KEY_PREFIX + orderCode;
    }

    private boolean isAlreadyProcessed(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    private void markAsProcessed(String key) {
        redisTemplate.opsForValue().set(key, "processed", Math.max(1L, idempotencyTtlHours), TimeUnit.HOURS);
    }
}
