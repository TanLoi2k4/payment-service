package com.tlcn.payment_service.kafka;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.tlcn.payment_service.dto.kafka.PaymentFailed;
import com.tlcn.payment_service.dto.kafka.PaymentSuccess;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.topics.payment-success:payment-success}")
    private String paymentSuccessTopic;

    @Value("${spring.kafka.topics.payment-failed:payment-failed}")
    private String paymentFailedTopic;

    public void publishPaymentSuccess(String key, PaymentSuccess event) {
        String topic = Objects.requireNonNull(paymentSuccessTopic, "paymentSuccessTopic must not be null");
        String messageKey = Objects.requireNonNull(key, "Kafka message key must not be null");
        log.info("Publishing PaymentSuccess to topic={}, key={}, orderCode={}", topic, key, event.getOrderCode());
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, messageKey, event);
        record.headers().add("messageId", messageKey.getBytes(StandardCharsets.UTF_8));
        record.headers().add("idempotency-key", messageKey.getBytes(StandardCharsets.UTF_8));
        record.headers().add("timestamp", String.valueOf(Instant.now().toEpochMilli()).getBytes(StandardCharsets.UTF_8));

        CompletableFuture<SendResult<String, Object>> sendFuture = kafkaTemplate.send(record);
        sendFuture.whenComplete((result, ex) -> handleSendResult(topic, messageKey, event.getOrderCode(), "PaymentSuccess", result, ex));
    }

    public void publishPaymentFailed(String key, PaymentFailed event) {
        String topic = Objects.requireNonNull(paymentFailedTopic, "paymentFailedTopic must not be null");
        String messageKey = Objects.requireNonNull(key, "Kafka message key must not be null");
        log.info("Publishing PaymentFailed to topic={}, key={}, orderCode={}", topic, key, event.getOrderCode());
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, messageKey, event);
        record.headers().add("messageId", messageKey.getBytes(StandardCharsets.UTF_8));
        record.headers().add("idempotency-key", messageKey.getBytes(StandardCharsets.UTF_8));
        record.headers().add("timestamp", String.valueOf(Instant.now().toEpochMilli()).getBytes(StandardCharsets.UTF_8));

        CompletableFuture<SendResult<String, Object>> sendFuture = kafkaTemplate.send(record);
        sendFuture.whenComplete((result, ex) -> handleSendResult(topic, messageKey, event.getOrderCode(), "PaymentFailed", result, ex));
    }

    private void handleSendResult(
        String topic,
        String key,
        String orderCode,
        String eventType,
        SendResult<String, Object> result,
        Throwable ex
    ) {
        if (ex != null) {
            log.error("Failed to publish {} to topic={}, key={}, orderCode={}", eventType, topic, key, orderCode, ex);
            return;
        }

        if (result != null && result.getRecordMetadata() != null) {
            log.info(
                "Published {} to topic={}, key={}, orderCode={}, partition={}, offset={}",
                eventType,
                topic,
                key,
                orderCode,
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset()
            );
            return;
        }

        log.info("Published {} to topic={}, key={}, orderCode={}", eventType, topic, key, orderCode);
    }
}
