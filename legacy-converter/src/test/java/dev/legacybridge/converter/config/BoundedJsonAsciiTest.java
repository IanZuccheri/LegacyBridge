package dev.legacybridge.converter.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class BoundedJsonAsciiTest {
    @ParameterizedTest
    @ValueSource(strings = {"\u0661", "\uff11", "1\u0661", "1.\u0661", "1e\u0661", "-\uff11", "0\u0661"})
    void rejectsNonAsciiNumericDigits(String value) {
        assertThrows(BoundedJsonParser.JsonSyntaxException.class, () -> BoundedJsonParser.parse(value));
    }
    @ParameterizedTest
    @ValueSource(strings = {"\uff10\uff10\uff14\uff11", "\uff21BCD", "\u0660\u0660\u0664\u0661"})
    void rejectsNonAsciiHexInUnicodeEscapes(String value) {
        assertThrows(BoundedJsonParser.JsonSyntaxException.class, () -> BoundedJsonParser.parse("\"\\u" + value + "\""));
    }
    @Test void keepsValidNumbersAndUnicodeStringContent() throws Exception {
        assertEquals(new BigDecimal("-1.25e2"), BoundedJsonParser.parse("-1.25e2"));
        assertEquals("A\u0661", BoundedJsonParser.parse("\"\\u0041\u0661\""));
    }
}
