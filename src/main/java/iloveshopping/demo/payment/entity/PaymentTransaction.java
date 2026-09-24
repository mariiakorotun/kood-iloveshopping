package iloveshopping.demo.payment.entity;

import iloveshopping.demo.order.entity.Order;
import iloveshopping.demo.shared.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions")
@Getter @Setter @NoArgsConstructor
public class PaymentTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(nullable = false) private String status;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal amount;
    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 2048) private String providerToken;
    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 2048) private String providerResponse;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
}
