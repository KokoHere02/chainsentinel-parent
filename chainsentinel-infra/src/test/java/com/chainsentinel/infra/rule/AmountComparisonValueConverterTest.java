package com.chainsentinel.infra.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class AmountComparisonValueConverterTest {

    @Test
    void shouldConvertMinimalUnitStringToBigInteger() {
        assertEquals(new BigInteger("100"), AmountComparisonValueConverter.toComparisonValue("000100"));
    }

    @Test
    void shouldConvertWholeNumberToBigInteger() {
        assertEquals(new BigInteger("42"), AmountComparisonValueConverter.toComparisonValue(42));
    }

    @Test
    void shouldRejectDecimalString() {
        assertThrows(IllegalArgumentException.class, () -> AmountComparisonValueConverter.toComparisonValue("1.2"));
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> AmountComparisonValueConverter.toComparisonValue(-1));
    }
}
