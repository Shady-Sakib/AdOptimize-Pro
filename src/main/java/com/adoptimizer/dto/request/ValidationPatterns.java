package com.adoptimizer.dto.request;

/** Regular expressions shared by several request DTOs. */
public final class ValidationPatterns {

    /** Letters (any language), spaces, apostrophes, periods and hyphens; must start with a letter. */
    public static final String PERSON_NAME = "^\\p{L}[\\p{L} .'-]*$";

    /** Requires a dot in the domain part, which {@code @Email} alone does not. */
    public static final String EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$";

    /** At least one letter and one digit. */
    public static final String PASSWORD = "^(?=.*\\p{L})(?=.*\\d).+$";

    private ValidationPatterns() {
    }
}
