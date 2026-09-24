package iloveshopping.demo.order.dto;

public record PaymentStatusEvent(
        Long orderId,
        boolean success,
        String trackingNumber,
        String failureReason
) {}
