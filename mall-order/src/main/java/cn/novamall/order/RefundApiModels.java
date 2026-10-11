package cn.novamall.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class RefundApiModels {
    private RefundApiModels() { }

    public record Line(@NotNull @Positive Long orderItemId, @Min(1) int quantity) { }
    public record QuoteRequest(@NotEmpty @Size(max = 100) List<@NotNull @Valid Line> items) { }
    public record ApplyRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String requestKey,
            @NotBlank @Size(max = 500) String reason,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal expectedAmount,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid Line> items,
            @Pattern(regexp = "REFUND_ONLY|RETURN_REFUND") String expectedMode) { }
    public record ReviewRequest(boolean approve, @NotBlank @Size(max = 500) String note,
            @Size(max = 64) String receiverName, @Size(max = 32) String receiverPhone,
            @Size(max = 255) String returnAddress) { }
    public record LogisticsRequest(@NotBlank @Size(max = 64) String carrier,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{6,64}") String trackingNo, @Min(0) int version) { }
    public record RestockLine(@NotNull @Positive Long orderItemId, @Min(0) int quantity) { }
    public record InspectRequest(boolean accepted, @NotBlank @Size(max = 500) String note,
            @Min(0) int version, @NotNull @Size(max = 100) List<@NotNull @Valid RestockLine> restockItems) { }
    public record RecheckRequest(@NotBlank @Size(max = 500) String note, @Min(0) int version) { }
}
