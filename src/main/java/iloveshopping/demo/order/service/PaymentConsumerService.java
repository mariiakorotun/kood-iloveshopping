package iloveshopping.demo.order.service;

import iloveshopping.demo.order.config.RabbitMQConfig;
import iloveshopping.demo.order.dto.OrderCreatedEvent;
import iloveshopping.demo.order.dto.PaymentStatusEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class PaymentConsumerService {

    private static final Logger log = LoggerFactory.getLogger(PaymentConsumerService.class);

    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitMQConfig.ORDER_CREATED_QUEUE)
    public void processPayment(OrderCreatedEvent event) {
        // Sandbox tokens deliberately model common gateway responses. No PAN, expiry, or CVV enters this service.
        String token = event.paymentMethodToken();
        String failureReason = switch (token == null ? "" : token) {
            case "tok_insufficient_funds" -> "Insufficient funds";
            case "tok_invalid_card" -> "Invalid card number";
            case "tok_expired_card" -> "Expired card";
            case "tok_gateway_timeout" -> "Payment gateway timeout";
            case "" -> "Missing payment token";
            default -> null;
        };
        boolean isSuccess = failureReason == null;
        String trackingNumber = isSuccess ? "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() : null;

        PaymentStatusEvent statusEvent = new PaymentStatusEvent(event.orderId(), isSuccess, trackingNumber, failureReason);

        rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_EXCHANGE, RabbitMQConfig.PAYMENT_ROUTING_KEY, statusEvent);
        log.info("Payment notification queued for order {}: {}", event.orderId(), isSuccess ? "successful" : "failed");
    }
}
