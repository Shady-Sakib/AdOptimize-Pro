package com.adoptimizer.validation;

import com.adoptimizer.dto.request.CampaignRequest;
import com.adoptimizer.dto.request.PaymentRequest;
import com.adoptimizer.dto.request.RegisterRequest;
import com.adoptimizer.dto.request.SettingsRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Custom constraints and the request DTO rules, run through a real Hibernate Validator. */
class ValidationRulesTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static <T> Set<String> invalidFields(T bean) {
        return validator.validate(bean).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    // ------------------------------------------------------------------ Luhn

    @ParameterizedTest
    @ValueSource(strings = {"4242424242424242", "4242 4242 4242 4242", "5555-5555-5555-4444", "378282246310005"})
    void acceptsValidCardNumbers(String number) {
        assertTrue(LuhnCardNumberValidator.isValidNumber(number));
    }

    @ParameterizedTest
    @ValueSource(strings = {"4242424242424241", "1234", "abcdabcdabcdabcd", "42424242424242424242"})
    void rejectsInvalidCardNumbers(String number) {
        assertFalse(LuhnCardNumberValidator.isValidNumber(number));
    }

    // ------------------------------------------------------------------ expiry

    @Test
    void expiryMustNotBeInThePastOrTooFarAhead() {
        YearMonth now = YearMonth.of(2026, 9);
        assertTrue(CardExpiryValidator.isValidExpiry("09/26", now), "current month is still valid");
        assertTrue(CardExpiryValidator.isValidExpiry("12/30", now));
        assertFalse(CardExpiryValidator.isValidExpiry("08/26", now), "last month has expired");
        assertFalse(CardExpiryValidator.isValidExpiry("13/27", now), "month 13 does not exist");
        assertFalse(CardExpiryValidator.isValidExpiry("1/27", now), "must be MM/YY");
        assertFalse(CardExpiryValidator.isValidExpiry("10/47", now), "more than 20 years ahead");
    }

    // ------------------------------------------------------------------ keywords

    @Test
    void keywordParsingTrimsLowercasesAndDeduplicates() {
        assertEquals(List.of("coats", "winter", "warm clothing"),
                KeywordListValidator.parse(" Coats, winter,  COATS , warm   clothing,,"));
        assertEquals(List.of(), KeywordListValidator.parse("   "));
    }

    // ------------------------------------------------------------------ DTOs

    @Test
    void campaignRequestReportsEveryBrokenField() {
        CampaignRequest request = new CampaignRequest();
        request.setTitle("ab");
        request.setDescription("short");
        request.setAudience("aliens");
        request.setAdType("hologram");
        request.setBudget(new BigDecimal("-5"));
        request.setStartDate(LocalDate.of(2026, 10, 10));
        request.setEndDate(LocalDate.of(2026, 10, 1));
        request.setKeywords("ok, bad!!word");

        assertEquals(Set.of("title", "description", "audience", "adType", "budget", "endDate", "keywords"),
                invalidFields(request));
    }

    @Test
    void validCampaignRequestPasses() {
        CampaignRequest request = new CampaignRequest();
        request.setTitle("Winter Coats Promo");
        request.setDescription("Warm coats with free delivery.");
        request.setAudience("gen-z");
        request.setAdType("video");
        request.setBudget(new BigDecimal("300.00"));
        request.setDailyBudget(new BigDecimal("12.50"));
        request.setStartDate(LocalDate.of(2026, 10, 1));
        request.setEndDate(LocalDate.of(2026, 10, 31));
        request.setKeywords("coats, winter");

        assertTrue(invalidFields(request).isEmpty());
    }

    @Test
    @DisplayName("Password confirmation mismatch is reported on confirmPassword")
    void registerRequestPasswordsMustMatch() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Jane O'Neil-Smith");
        request.setEmail("jane@example.com");
        request.setPassword("secret12");
        request.setConfirmPassword("secret13");

        assertEquals(Set.of("confirmPassword"), invalidFields(request));
    }

    @Test
    void registerRequestRejectsWeakPasswordAndEmailWithoutDomain() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Jane");
        request.setEmail("jane@localhost");
        request.setPassword("abcdefg");
        request.setConfirmPassword("abcdefg");

        assertEquals(Set.of("email", "password"), invalidFields(request));
    }

    @Test
    void paymentRequestValidatesCardFieldsAndAmount() {
        PaymentRequest request = new PaymentRequest();
        request.setCardHolder("Alex Morgan");
        request.setCardNumber("4242424242424241");
        request.setExpiry("01/20");
        request.setCvv("12");
        request.setAmount(new BigDecimal("5"));

        assertEquals(Set.of("cardNumber", "expiry", "cvv", "amount"), invalidFields(request));
    }

    @Test
    void settingsPeakHoursMustEndAfterTheyStart() {
        SettingsRequest request = new SettingsRequest();
        request.setCpcRate(new BigDecimal("0.45"));
        request.setCpmRate(new BigDecimal("2.50"));
        request.setMinBudget(new BigDecimal("50"));
        request.setMaxDailyBudget(new BigDecimal("10000"));
        request.setPlatformFee(new BigDecimal("5"));
        request.setPeakHoursStart(22);
        request.setPeakHoursEnd(18);
        request.setAutoApprove(false);
        request.setContentFilter(true);
        request.setBudgetAlerts(true);
        request.setSimulationEnabled(true);

        assertEquals(Set.of("peakHoursEnd"), invalidFields(request));
    }
}
