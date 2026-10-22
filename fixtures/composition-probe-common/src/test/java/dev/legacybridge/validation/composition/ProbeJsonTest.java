package dev.legacybridge.validation.composition;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Serialization tests use synthetic values; they are not native behavior observations. */
class ProbeJsonTest {
    private static String render(Map<String, Object> value) {
        return new String(ProbeJson.render(value), StandardCharsets.UTF_8);
    }

    private static String repeated(char value, int count) {
        char[] chars = new char[count]; Arrays.fill(chars, value); return new String(chars);
    }

    @Test void objectPreservesInsertionWhileRenderSortsEveryObjectAndUsesOneNewline() {
        LinkedHashMap<String, Object> value = ProbeJson.object("z", ProbeJson.object("y", 2, "a", 1), "a", "NOT_RUN");
        assertEquals(Arrays.asList("z", "a"), new ArrayList<String>(value.keySet()));
        assertEquals("{\"a\":\"NOT_RUN\",\"z\":{\"a\":1,\"y\":2}}\n", render(value));
        assertEquals("{}\n", render(ProbeJson.object()));
    }

    @Test void supportedPrimitiveValuesAndOrderedArraysAreRenderedExactly() {
        List<Object> values = Arrays.<Object>asList(null, true, false, 17, Long.MAX_VALUE, 1.5f, -2.25d, "mod:item-1.0+test");
        assertEquals("{\"values\":[null,true,false,17,9223372036854775807,1.5,-2.25,\"mod:item-1.0+test\"]}\n",
                render(ProbeJson.object("values", values)));
    }

    @Test void malformedPairsAndDuplicateKeysIncludingNullValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ProbeJson.object((Object[]) null));
        assertThrows(IllegalArgumentException.class, () -> ProbeJson.object("unpaired"));
        assertThrows(IllegalArgumentException.class, () -> ProbeJson.object(new Object[66]));
        assertThrows(IllegalArgumentException.class, () -> ProbeJson.object(1, "value"));
        assertThrows(IllegalArgumentException.class, () -> ProbeJson.object(null, "value"));
        assertThrows(IllegalArgumentException.class, () -> ProbeJson.object("same", null, "same", 1));
    }

    @Test void exactObjectLimitIsAcceptedAndOneMoreEntryIsRejected() {
        Map<String, Object> value = ProbeJson.object();
        for (int i = 0; i < 32; i++) value.put("key" + i, i);
        assertTrue(render(value).contains("\"key31\":31"));
        value.put("key32", 32);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> render(value)).getMessage().contains("OBS_OBJECT_LIMIT"));
    }

    @Test void exactArrayLimitIsAcceptedAndOneMoreEntryIsRejected() {
        List<Object> values = new ArrayList<Object>(Collections.<Object>nCopies(256, true));
        render(ProbeJson.object("values", values));
        values.add(true);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> render(ProbeJson.object("values", values)))
                .getMessage().contains("OBS_ARRAY_LIMIT"));
    }

    @Test void identifierBoundaryAndAllowedPunctuationAreExplicit() {
        String max = repeated('a', 256);
        assertEquals("{\"key\":\"" + max + "\"}\n", render(ProbeJson.object("key", max)));
        assertEquals("{\"A_0\":\"aZ09_.:+-\"}\n", render(ProbeJson.object("A_0", "aZ09_.:+-")));
    }

    @Test void unsafeOrUnboundedStringValuesAndKeysAreRefusedNotEscapedSilently() {
        String[] invalid = {"", "a b", "a/b", "a\\b", "quote\"", "line\n", "tab\t", "ol\u00e1", "\ud800", repeated('x', 257)};
        for (String value : invalid) {
            assertTrue(assertThrows(IllegalArgumentException.class, () -> render(ProbeJson.object("key", value)))
                    .getMessage().contains("OBS_IDENTIFIER"));
            assertThrows(IllegalArgumentException.class, () -> render(ProbeJson.object(value, true)));
        }
    }

    @Test void nonFiniteNumbersAreNeverSerializedAsInvalidJson() {
        Object[] invalid = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (Object value : invalid) assertTrue(assertThrows(IllegalArgumentException.class,
                () -> render(ProbeJson.object("number", value))).getMessage().contains("OBS_NONFINITE"));
    }

    @Test void unknownValuesAreRejectedInsteadOfUsingToString() {
        Object[] values = {new Object(), new byte[]{1}, Short.valueOf((short) 1), Byte.valueOf((byte) 1), new BigDecimal("1.25")};
        for (Object value : values) assertTrue(assertThrows(IllegalArgumentException.class,
                () -> render(ProbeJson.object("value", value))).getMessage().contains("OBS_VALUE"));
    }

    @Test void arbitraryMapsWithNonStringKeysAreRejected() {
        Map<Object, Object> nested = new LinkedHashMap<Object, Object>(); nested.put(1, true);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> render(ProbeJson.object("nested", nested)))
                .getMessage().contains("OBS_KEY"));
    }

    @Test void depthIsBoundedAndCyclesFailWithoutRecursingForever() {
        Object value = "leaf";
        for (int i = 0; i < 15; i++) value = Collections.singletonList(value);
        render(ProbeJson.object("value", value));
        final Object tooDeep = Collections.singletonList(value);
        assertThrows(IllegalArgumentException.class, () -> render(ProbeJson.object("value", tooDeep)));
        List<Object> cycle = new ArrayList<Object>(); cycle.add(cycle);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> render(ProbeJson.object("cycle", cycle)))
                .getMessage().contains("OBS_JSON_LIMIT"));
        Map<String, Object> mapCycle = ProbeJson.object(); mapCycle.put("self", mapCycle);
        assertThrows(IllegalArgumentException.class, () -> render(mapCycle));
    }

    @Test void nodeBudgetRejectsManySmallValues() {
        List<Object> groups = new ArrayList<Object>();
        for (int i = 0; i < 40; i++) groups.add(Collections.nCopies(256, 1));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> render(ProbeJson.object("groups", groups)))
                .getMessage().contains("OBS_JSON_LIMIT"));
    }

    @Test void characterBudgetRejectsLargeTreesBeforePublication() {
        List<Object> groups = new ArrayList<Object>();
        for (int i = 0; i < 20; i++) groups.add(Collections.nCopies(256, repeated('a', 256)));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> render(ProbeJson.object("groups", groups)))
                .getMessage().contains("OBS_JSON_LIMIT"));
    }
}
