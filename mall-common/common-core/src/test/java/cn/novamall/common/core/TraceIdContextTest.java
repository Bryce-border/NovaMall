package cn.novamall.common.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceIdContextTest {

    @Test
    void keepsSafeIncomingTraceId() {
        assertEquals("checkout-123", TraceIdContext.resolve(" checkout-123 "));
    }

    @Test
    void replacesInvalidTraceId() {
        String resolved = TraceIdContext.resolve("bad value");
        assertNotEquals("bad value", resolved);
        assertTrue(resolved.matches("[a-f0-9]{32}"));
    }
}
