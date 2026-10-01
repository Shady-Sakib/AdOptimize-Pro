package com.adoptimizer.dto.request;

import com.adoptimizer.model.AdType;
import com.adoptimizer.model.Audience;
import com.adoptimizer.validation.EnumCode;
import com.adoptimizer.validation.KeywordList;
import com.adoptimizer.validation.OrderedFields;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Create / edit campaign form. Rules that depend on admin settings or the advertiser's
 * wallet (minimum budget, daily cap, available funds) are checked in {@code CampaignService}.
 */
@Data
@OrderedFields(first = "startDate", second = "endDate", message = "End date must be after the start date")
public class CampaignRequest {

    @NotBlank(message = "Enter a campaign title")
    @Size(min = 3, max = 100, message = "Title must be 3–100 characters")
    private String title;

    @NotBlank(message = "Enter a description")
    @Size(min = 10, max = 1000, message = "Description must be 10–1000 characters")
    private String description;

    @NotBlank(message = "Select a target audience")
    @EnumCode(enumClass = Audience.class, message = "Select a valid target audience")
    private String audience;

    @NotBlank(message = "Select an ad format")
    @EnumCode(enumClass = AdType.class, message = "Select a valid ad format")
    private String adType;

    @NotNull(message = "Enter a total budget")
    @DecimalMin(value = "1.00", message = "Total budget must be greater than zero")
    @DecimalMax(value = "1000000.00", message = "Total budget cannot exceed $1,000,000")
    @Digits(integer = 10, fraction = 2, message = "Total budget can have at most 2 decimal places")
    private BigDecimal budget;

    /** Optional; defaults to budget / campaign length in days. */
    @DecimalMin(value = "5.00", message = "Daily budget must be at least $5")
    @DecimalMax(value = "1000000.00", message = "Daily budget cannot exceed $1,000,000")
    @Digits(integer = 10, fraction = 2, message = "Daily budget can have at most 2 decimal places")
    private BigDecimal dailyBudget;

    @NotNull(message = "Choose a start date")
    private LocalDate startDate;

    @NotNull(message = "Choose an end date")
    private LocalDate endDate;

    @Size(max = 700, message = "Keywords must be at most 700 characters in total")
    @KeywordList(max = 20)
    private String keywords;
}
