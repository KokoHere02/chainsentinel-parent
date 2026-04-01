package com.chainsentinel.infra.rule;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Converts minimal-unit amount inputs into normalized integer comparison values.
 */
public final class AmountComparisonValueConverter {

    private AmountComparisonValueConverter() {
    }

    public static BigInteger toComparisonValue(Object value) {
        if (value == null) {
            return BigInteger.ZERO;
        }
        if (value instanceof BigInteger bigInteger) {
            return requireNonNegative(bigInteger);
        }
        if (value instanceof BigDecimal bigDecimal) {
            return toInteger(bigDecimal);
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return requireNonNegative(BigInteger.valueOf(((Number) value).longValue()));
        }
        if (value instanceof Float || value instanceof Double) {
            return toInteger(BigDecimal.valueOf(((Number) value).doubleValue()));
        }

        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("amount value is blank");
        }
        if (!text.matches("^\\d+$")) {
            throw new IllegalArgumentException("amount must be a non-negative integer string: " + text);
        }
        return new BigInteger(text);
    }

    private static BigInteger toInteger(BigDecimal decimal) {
        BigDecimal normalized = decimal.stripTrailingZeros();
        if (normalized.scale() > 0) {
            throw new IllegalArgumentException("amount must be integer in minimal unit: " + decimal);
        }
        return requireNonNegative(normalized.toBigIntegerExact());
    }

    private static BigInteger requireNonNegative(BigInteger value) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException("amount must be non-negative: " + value);
        }
        return value;
    }
}
