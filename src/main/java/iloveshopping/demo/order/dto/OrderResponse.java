package iloveshopping.demo.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String status,
        BigDecimal totalAmount,
        String customerEmail,
        String customerFirstName,
        String customerLastName,
        String customerPhone,
        String shippingAddress,
        String shippingMethod,
        String trackingNumber,
        List<OrderItemResponse> items,
        LocalDateTime createdAt
) {}
