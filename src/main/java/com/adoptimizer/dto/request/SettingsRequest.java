package com.adoptimizer.dto.request;

import com.adoptimizer.validation.OrderedFields;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@OrderedFields(first = "peakHoursStart", second = "peakHoursEnd", message = "Peak hours must end after they start")
public class SettingsRequest {

    @NotNull(message = "Enter the cost per click")
    @DecimalMin(value = "0.01", message = "Cost per click must be at least $0.01")
    @DecimalMax(value = "100.00", message = "Cost per click cannot exceed $100")
    @Digits(integer = 6, fraction = 2, message = "Cost per click can have at most 2 decimal places")
    private BigDecimal cpcRate;

    @NotNull(message = "Enter the cost per 1,000 impressions")
    @DecimalMin(value = "0.01", message = "CPM must be at least $0.01")
    @DecimalMax(value = "1000.00", message = "CPM cannot exceed $1,000")
    @Digits(integer = 6, fraction = 2, message = "CPM can have at most 2 decimal places")
    private BigDecimal cpmRate;

    @NotNull(message = "Enter the minimum campaign budget")
    @DecimalMin(value = "10.00", message = "Minimum budget must be at least $10")
    @DecimalMax(value = "100000.00", message = "Minimum budget cannot exceed $100,000")
    @Digits(integer = 10, fraction = 2, message = "Minimum budget can have at most 2 decimal places")
    private BigDecimal minBudget;

    @NotNull(message = "Enter the maximum daily budget")
    @DecimalMin(value = "5.00", message = "Maximum daily budget must be at least $5")
    @DecimalMax(value = "1000000.00", message = "Maximum daily budget cannot exceed $1,000,000")
    @Digits(integer = 10, fraction = 2, message = "Maximum daily budget can have at most 2 decimal places")
    private BigDecimal maxDailyBudget;

    @NotNull(message = "Enter the platform fee")
    @DecimalMin(value = "1.00", message = "Platform fee must be at least 1%")
    @DecimalMax(value = "30.00", message = "Platform fee cannot exceed 30%")
    @Digits(integer = 3, fraction = 2, message = "Platform fee can have at most 2 decimal places")
    private BigDecimal platformFee;

    @NotNull(message = "Enter the peak hours start")
    @Min(value = 0, message = "Peak hours start must be between 0 and 23")
    @Max(value = 23, message = "Peak hours start must be between 0 and 23")
    private Integer peakHoursStart;

    @NotNull(message = "Enter the peak hours end")
    @Min(value = 0, message = "Peak hours end must be between 0 and 23")
    @Max(value = 23, message = "Peak hours end must be between 0 and 23")
    private Integer peakHoursEnd;

    @NotNull(message = "Choose whether ads are auto-approved")
    private Boolean autoApprove;

    @NotNull(message = "Choose whether the content filter is on")
    private Boolean contentFilter;

    @NotNull(message = "Choose whether budget alerts are on")
    private Boolean budgetAlerts;

    @NotNull(message = "Choose whether the traffic simulator is on")
    private Boolean simulationEnabled;

    @Size(max = 1000, message = "Blocked words must be at most 1000 characters")
    private String bannedWords;
}
