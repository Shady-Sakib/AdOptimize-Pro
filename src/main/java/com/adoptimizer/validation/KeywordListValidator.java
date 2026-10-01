package com.adoptimizer.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

public class KeywordListValidator implements ConstraintValidator<KeywordList, String> {

    public static final Pattern KEYWORD = Pattern.compile("^[\\p{L}\\p{N}][\\p{L}\\p{N} &'-]{1,29}$");

    private int max;

    @Override
    public void initialize(KeywordList annotation) {
        this.max = annotation.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        List<String> keywords = parse(value);
        return keywords.size() <= max && keywords.stream().allMatch(keyword -> KEYWORD.matcher(keyword).matches());
    }

    /** Splits, trims, lower-cases and de-duplicates a comma-separated keyword string. */
    public static List<String> parse(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.copyOf(Arrays.stream(value.split(","))
                .map(String::trim)
                .map(keyword -> keyword.replaceAll("\\s+", " ").toLowerCase())
                .filter(keyword -> !keyword.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }
}
