package com.adoptimizer.service;

import com.adoptimizer.model.Campaign;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceHelpersTest {

    @Test
    void csvCellsAreQuotedAndFormulaInjectionIsNeutralised() {
        assertEquals("\"plain\"", ReportService.cell("plain"));
        assertEquals("\"say \"\"hi\"\"\"", ReportService.cell("say \"hi\""));
        assertEquals("\"'=HYPERLINK(1)\"", ReportService.cell("=HYPERLINK(1)"));
        assertEquals("\"-12.50\"", ReportService.cell("-12.50"), "negative numbers stay numbers");
        assertEquals("\"\"", ReportService.cell(null));
        assertEquals("\"a\",\"b\"\r\n", ReportService.toCsv(List.of(List.of("a", "b"))));
    }

    @Test
    void cardBrandDetection() {
        assertEquals("Visa", PaymentService.detectBrand("4242424242424242"));
        assertEquals("Mastercard", PaymentService.detectBrand("5555555555554444"));
        assertEquals("Mastercard", PaymentService.detectBrand("2223003122003222"));
        assertEquals("Amex", PaymentService.detectBrand("378282246310005"));
        assertEquals("Discover", PaymentService.detectBrand("6011111111111117"));
        assertEquals("Card", PaymentService.detectBrand("3530111333300000"));
    }

    @Test
    void blockedWordsAreNormalised() {
        assertEquals("free money,guaranteed,spam", SettingsService.normalizeWords(" Free Money, guaranteed,\nSPAM, guaranteed ,,"));
    }

    @Test
    void contentFilterFindsBlockedWordsInAnyField() {
        Campaign campaign = Campaign.builder().title("Winter sale").description("Great coats for everyone")
                .keywords(new ArrayList<>(List.of("get rich"))).build();
        assertEquals("get rich", ModerationService.findBlockedWord(campaign, List.of("miracle", "get rich")).orElseThrow());
        assertTrue(ModerationService.findBlockedWord(campaign, List.of("miracle")).isEmpty());
    }
}
