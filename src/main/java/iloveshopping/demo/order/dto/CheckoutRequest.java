package iloveshopping.demo.order.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CheckoutRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "First name is required")
        String firstName,

        @NotBlank(message = "Last name is required")
        String lastName,

        @NotBlank(message = "Address is required")
        @Size(min = 5, max = 200, message = "Address must be between 5 and 200 characters")
        String address,

        @NotBlank(message = "City is required")
        String city,

        @NotBlank(message = "Zip code is required")
        @Pattern(regexp = "^[A-Za-z0-9 -]{3,12}$", message = "Invalid zip code format")
        String zipCode,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[+0-9][0-9 ()-]{6,20}$", message = "Invalid phone number format")
        String phone,

        @NotBlank(message = "Shipping option is required")
        String shippingOptionId,

        @NotBlank(message = "Payment method token is required")
        String paymentMethodToken
) {}
