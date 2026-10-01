package com.adoptimizer.dto.response;

import java.util.List;

/**
 * One optimization suggestion.
 *
 * @param priority   {@code high}, {@code medium} or {@code low}
 * @param applicable whether the "apply" button changes the campaign automatically
 */
public record SuggestionResponse(
        String type,
        String icon,
        String title,
        String description,
        String priority,
        String priorityLabel,
        List<String> items,
        String actionLabel,
        boolean applicable) {
}
