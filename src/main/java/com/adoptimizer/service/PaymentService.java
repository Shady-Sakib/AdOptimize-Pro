package com.adoptimizer.service;

import com.adoptimizer.dto.request.PaymentRequest;
import com.adoptimizer.dto.response.PaymentResponse;
import com.adoptimizer.model.NotificationType;
import com.adoptimizer.model.Payment;
import com.adoptimizer.repository.PaymentRepository;
import com.adoptimizer.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.List;

/**
 * Simulated card payments (no real gateway). The card is validated (Luhn, expiry, CVV) but only the brand and the
 * last four digits are stored — never the full number or the CVV.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;

    public List<PaymentResponse> history(long userId) {
        return paymentRepository.findByUser(userId, 100).stream().map(PaymentResponse::from).toList();
    }

    @Transactional
    public PaymentResponse addFunds(long userId, PaymentRequest request) {
        String digits = request.getCardNumber().replaceAll("[\\s-]", "");
        Payment payment = Payment.builder()
                .userId(userId)
                .amount(request.getAmount().setScale(2, RoundingMode.HALF_UP))
                .cardBrand(detectBrand(digits))
                .cardLast4(digits.substring(digits.length() - 4))
                .description("Account top-up")
                .status(Payment.STATUS_COMPLETED)
                .createdAt(TimeUtils.now())
                .build();
        payment.setId(paymentRepository.insert(payment));
        notificationService.notify(userId, NotificationType.SUCCESS, "Payment successful",
                "$" + payment.getAmount().toPlainString() + " was added to your account using " + payment.getMethod() + ".");
        return PaymentResponse.from(payment);
    }

    public static String detectBrand(String digits) {
        if (digits.startsWith("4")) {
            return "Visa";
        }
        if (digits.startsWith("34") || digits.startsWith("37")) {
            return "Amex";
        }
        if (digits.startsWith("6011") || digits.startsWith("65")) {
            return "Discover";
        }
        if (digits.length() >= 4) {
            int prefix2 = Integer.parseInt(digits.substring(0, 2));
            int prefix4 = Integer.parseInt(digits.substring(0, 4));
            if ((prefix2 >= 51 && prefix2 <= 55) || (prefix4 >= 2221 && prefix4 <= 2720)) {
                return "Mastercard";
            }
        }
        return "Card";
    }
}
