package iloveshopping.demo.order.service;

import iloveshopping.demo.cart.entity.Cart;
import iloveshopping.demo.cart.repository.CartRepository;
import iloveshopping.demo.order.config.RabbitMQConfig;
import iloveshopping.demo.order.dto.*;
import iloveshopping.demo.order.entity.Order;
import iloveshopping.demo.order.entity.OrderItem;
import iloveshopping.demo.order.repository.OrderRepository;
import iloveshopping.demo.user.entity.User;
import iloveshopping.demo.catalog.repository.ProductRepository;
import iloveshopping.demo.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final OrderMessageProducer messageProducer;
    private final ProductRepository productRepository;
    private final NotificationService notificationService;

    @Transactional
    public OrderResponse checkout(User user, String guestSessionId, CheckoutRequest request) {
        Cart cart = getCartEntity(user, guestSessionId);

        if (cart.getItems().isEmpty()) {
            throw new IllegalStateException("Cannot checkout with an empty cart");
        }

        Order order = new Order();
        order.setUser(user);
        order.setCustomerEmail(request.email());
        order.setCustomerPhone(request.phone());
        order.setShippingAddress(String.format("%s, %s, %s", request.address(), request.city(), request.zipCode()));
        order.setShippingMethod(request.shippingOptionId());
        order.setStatus("PENDING_PAYMENT");

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (var cartItem : cart.getItems()) {
            var product = productRepository.findByIdForUpdate(cartItem.getProduct().getId())
                    .orElseThrow(() -> new IllegalStateException("A product in this cart is no longer available"));
            if (product.getStockQuantity() < cartItem.getQuantity()) {
                throw new IllegalStateException("Insufficient stock for " + product.getName());
            }
            product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());
            OrderItem orderItem = new OrderItem();
            orderItem.setProductId(cartItem.getProduct().getId());
            orderItem.setProductName(cartItem.getProduct().getName());
            orderItem.setPriceAtPurchase(cartItem.getProduct().getPrice());
            orderItem.setQuantity(cartItem.getQuantity());

            order.addItem(orderItem);
            BigDecimal itemTotal = cartItem.getProduct().getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            totalAmount = totalAmount.add(itemTotal);
        }

        order.setTotalAmount(totalAmount);
        Order savedOrder = orderRepository.save(order);

        cart.getItems().clear();
        cartRepository.save(cart);

        // Відправляємо подію в RabbitMQ
        messageProducer.sendOrderCreatedEvent(
                new OrderCreatedEvent(savedOrder.getId(), savedOrder.getCustomerEmail(), savedOrder.getTotalAmount(), request.paymentMethodToken())
        );

        return mapToOrderResponse(savedOrder);
    }

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_STATUS_QUEUE)
    @Transactional
    public void handlePaymentStatus(PaymentStatusEvent event) {
        Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) return;

        if (event.success()) {
            order.setStatus("PAYMENT_SUCCESSFUL");
            order.setTrackingNumber(event.trackingNumber());
        } else {
            // Checkout reserves stock before the provider call. Release it exactly once on a failed callback.
            if ("PENDING_PAYMENT".equals(order.getStatus())) {
                order.getItems().forEach(item -> productRepository.findByIdForUpdate(item.getProductId())
                        .ifPresent(product -> product.setStockQuantity(product.getStockQuantity() + item.getQuantity())));
            }
            order.setStatus("PAYMENT_FAILED");
        }
        orderRepository.save(order);
        notificationService.sendPaymentUpdate(order, event.success());
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId, User user) {
        Order order = getOrderEntity(orderId, user);

        if ("SHIPPED".equals(order.getStatus()) || "DELIVERED".equals(order.getStatus())) {
            throw new IllegalStateException("Cannot cancel order that is already shipped or delivered");
        }

        if ("CANCELLED".equals(order.getStatus())) {
            throw new IllegalStateException("Order is already cancelled");
        }

        if ("PAYMENT_SUCCESSFUL".equals(order.getStatus())) {
            // Workflow повернення коштів (Refund)
            order.setRefunded(true);
        }

        order.setStatus("CANCELLED");
        order.getItems().forEach(item -> productRepository.findByIdForUpdate(item.getProductId())
                .ifPresent(product -> product.setStockQuantity(product.getStockQuantity() + item.getQuantity())));
        return mapToOrderResponse(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> filterOrders(User user, String status, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = (startDate != null) ? startDate.atStartOfDay() : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime end = (endDate != null) ? endDate.atTime(LocalTime.MAX) : LocalDateTime.now();

        return orderRepository.findByUserIdAndCreatedAtBetween(user.getId(), start, end)
                .stream()
                .filter(order -> (status == null) || status.isBlank() || order.getStatus().equalsIgnoreCase(status))
                .map(this::mapToOrderResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId, User user) {
        return mapToOrderResponse(getOrderEntity(orderId, user));
    }

    private Order getOrderEntity(Long orderId, User user) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with id: " + orderId));

        if (user != null && order.getUser() != null && !order.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("You don't have access to this order");
        }
        return order;
    }

    private Cart getCartEntity(User user, String guestSessionId) {
        if (user != null) {
            return cartRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new IllegalStateException("Cart not found for user"));
        }
        if (guestSessionId != null) {
            return cartRepository.findByGuestSessionId(guestSessionId)
                    .orElseThrow(() -> new IllegalStateException("Cart not found for guest session"));
        }
        throw new IllegalArgumentException("Either user or guestSessionId must be provided");
    }

    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getId(),
                        item.getProductId(),
                        item.getProductName(),
                        item.getPriceAtPurchase(),
                        item.getQuantity(),
                        item.getPriceAtPurchase().multiply(BigDecimal.valueOf(item.getQuantity()))
                )).toList();

        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCustomerEmail(),
                order.getCustomerPhone(),
                order.getShippingAddress(),
                order.getShippingMethod(),
                itemResponses,
                order.getCreatedAt()
        );
    }
}
