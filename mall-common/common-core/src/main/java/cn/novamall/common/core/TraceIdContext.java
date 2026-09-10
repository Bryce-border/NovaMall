package cn.novamall.common.core;

import java.util.UUID;
import java.util.regex.Pattern;

/** Shared trace ID rules used at the gateway and by servlet services. */
public final class TraceIdContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    public static final String HEADER_NAME = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    private static final int MAX_LENGTH = 128;
    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    private TraceIdContext() {
    }

    public static String resolve(String candidate) {
        if (candidate != null) {
            String value = candidate.trim();
            if (value.length() <= MAX_LENGTH && SAFE_VALUE.matcher(value).matches()) {
                return value;
            }
        }
        return generate();
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static void setCurrent(String traceId) { CURRENT.set(traceId); }
    public static String current() { return CURRENT.get(); }
    public static void clearCurrent() { CURRENT.remove(); }
}
