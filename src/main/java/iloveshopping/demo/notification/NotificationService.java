package iloveshopping.demo.notification;

import iloveshopping.demo.order.entity.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final JavaMailSender mailSender;

    public void sendPaymentUpdate(Order order, boolean successful) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(order.getCustomerEmail());
        message.setFrom("orders@iloveshopping.local");
        message.setSubject(successful ? "Your order payment was successful" : "Your order payment failed");
        message.setText(successful
                ? "Thank you for your order #" + order.getId() + ". Payment was successful. Tracking: " + order.getTrackingNumber() + "."
                : "We could not process payment for order #" + order.getId() + ". No charge was made and reserved inventory was released.");
        mailSender.send(message);
    }
}
