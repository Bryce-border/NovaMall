package cn.novamall.product;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.Map;

public final class SkuApiModels {
    private SkuApiModels() { }

    public record SkuResponse(
            @JsonFormat(shape = JsonFormat.Shape.STRING) Long skuId,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Long productId,
            String skuCode, String productName, String skuName, Map<String, String> specs,
            String coverImageUrl,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal price,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal marketPrice,
            Integer status) { }
}
