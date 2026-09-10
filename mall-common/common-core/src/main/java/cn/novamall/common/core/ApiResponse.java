package cn.novamall.common.core;


public record ApiResponse<T>(boolean success, String code, T data, String message, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "0", data, "OK", currentTraceId());
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, "BAD_REQUEST", null, message, currentTraceId());
    }

    private static String currentTraceId() { return TraceIdContext.current(); }
}
