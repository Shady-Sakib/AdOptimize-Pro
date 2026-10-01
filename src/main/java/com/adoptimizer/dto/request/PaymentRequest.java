package com.adoptimizer.dto.request;

import com.adoptimizer.validation.CardExpiry;
import com.adoptimizer.validation.LuhnCardNumber;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;

/** Add-funds form. Card number and CVV are validated, never stored or logged. */
@Data
public class PaymentRequest {

    @NotBlank(message = "Enter the cardholder name")
    @Size(min = 2, max = 60, message = "Cardholder name must be 2–60 characters")
    @Pattern(regexp = ValidationPatterns.PERSON_NAME, message = "Cardholder name can only contain letters and spaces")
    private String cardHolder;

    @NotBlank(message = "Enter the card number")
    @LuhnCardNumber(message = "Enter a valid card number")
    @ToString.Exclude
    private String cardNumber;

    @NotBlank(message = "Enter the expiry date")
    @CardExpiry
    private String expiry;

    @NotBlank(message = "Enter the CVV")
    @Pattern(regexp = "^\\d{3,4}$", message = "CVV must be 3 or 4 digits")
    @ToString.Exclude
    private String cvv;

    @NotNull(message = "Enter an amount")
    @DecimalMin(value = "10.00", message = "Minimum payment amount is $10")
    @DecimalMax(value = "50000.00", message = "Maximum payment amount is $50,000")
    @Digits(integer = 8, fraction = 2, message = "Amount can have at most 2 decimal places")
    private BigDecimal amount;
}
