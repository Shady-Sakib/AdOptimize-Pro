package com.adoptimizer.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Enum whose values are exposed to the browser as short lower-case codes
 * (for example {@code "gen-z"} or {@code "active"}) instead of Java constant names.
 */
public interface CodedEnum {

    String getCode();

    static <E extends Enum<E> & CodedEnum> Optional<E> fromCode(Class<E> type, String code) {
        if (code == null) {
            return Optional.empty();
        }
        String normalized = code.trim().toLowerCase();
        return Arrays.stream(type.getEnumConstants())
                .filter(value -> value.getCode().equals(normalized))
                .findFirst();
    }

    static <E extends Enum<E> & CodedEnum> E requireCode(Class<E> type, String code) {
        return fromCode(type, code).orElseThrow(
                () -> new IllegalArgumentException("Unknown " + type.getSimpleName() + " code: " + code));
    }
}
