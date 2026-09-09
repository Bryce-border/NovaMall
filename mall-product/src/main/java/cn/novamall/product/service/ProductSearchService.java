package cn.novamall.product.service;

import cn.novamall.product.entity.ProductDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProductSearchService {
    private static final String INDEX = "mall_products";
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newHttpClient();
    private final String endpoint;

    public ProductSearchService(ObjectMapper mapper,
                                @Value("${spring.elasticsearch.uris:http://localhost:9200}") String endpoint) {
        this.mapper = mapper; this.endpoint = endpoint.replaceAll("/$", "");
    }

    public void index(ProductDocument document) { request("PUT", "/" + INDEX + "/_doc/" + document.getId(), document); }
    public void delete(Long id) { request("DELETE", "/" + INDEX + "/_doc/" + id, null); }

    public SearchResult search(String keyword, Long categoryId, java.math.BigDecimal minPrice,
                             java.math.BigDecimal maxPrice, long page, long size) {
        var filters = mapper.createArrayNode();
        filters.add(mapper.createObjectNode().set("term", mapper.createObjectNode().put("status", 1)));
        if (categoryId != null) filters.add(mapper.createObjectNode().set("term", mapper.createObjectNode().put("categoryId", categoryId)));
        if (minPrice != null || maxPrice != null) {
            var range = mapper.createObjectNode();
            if (minPrice != null) range.put("gte", minPrice);
            if (maxPrice != null) range.put("lte", maxPrice);
            filters.add(mapper.createObjectNode().set("range", mapper.createObjectNode().set("price", range)));
        }
        ObjectNode bool = mapper.createObjectNode().set("filter", filters);
        if (keyword == null || keyword.isBlank()) bool.set("must", mapper.createObjectNode().putObject("match_all"));
        else bool.set("must", mapper.createObjectNode().set("multi_match", mapper.createObjectNode()
                .put("query", keyword.trim()).put("operator", "and")
                .set("fields", mapper.valueToTree(List.of("name", "subtitle")))));
        ObjectNode body = mapper.createObjectNode().set("query", mapper.createObjectNode().set("bool", bool));
        body.put("from", (page - 1) * size); body.put("size", size);
        JsonNode response = request("POST", "/" + INDEX + "/_search", body);
        List<Long> ids = new ArrayList<>();
        long total = response == null ? 0 : response.path("hits").path("total").isObject()
                ? response.path("hits").path("total").path("value").asLong() : response.path("hits").path("total").asLong();
        if (response != null) for (JsonNode hit : response.path("hits").path("hits")) ids.add(Long.valueOf(hit.path("_id").asText()));
        return new SearchResult(ids, total);
    }

    public record SearchResult(List<Long> ids, long total) { }

    private JsonNode request(String method, String path, Object body) {
        try {
            String json = body == null ? "" : mapper.writeValueAsString(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint + path)).header("Content-Type", "application/json");
            HttpRequest request = switch (method) {
                case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(json)).build();
                case "DELETE" -> builder.DELETE().build();
                default -> builder.POST(HttpRequest.BodyPublishers.ofString(json)).build();
            };
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) throw new IllegalStateException("ES HTTP " + response.statusCode());
            return response.body().isBlank() ? null : mapper.readTree(response.body());
        } catch (Exception ex) { throw new IllegalStateException("Elasticsearch request failed", ex); }
    }
}
