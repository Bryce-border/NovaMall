package cn.novamall.product.entity;


import java.math.BigDecimal;

public class ProductDocument {
    private Long id;
    private Long categoryId;
    private String name;
    private String subtitle;
    private BigDecimal price;
    private Integer status;

    public ProductDocument() { }

    public ProductDocument(ProductEntity product) {
        this.id = product.getId(); this.categoryId = product.getCategoryId();
        this.name = product.getName(); this.subtitle = product.getSubtitle();
        this.price = product.getPrice(); this.status = product.getStatus();
    }
    public Long getId() { return id; }
    public Long getCategoryId() { return categoryId; }
    public String getName() { return name; }
    public String getSubtitle() { return subtitle; }
    public BigDecimal getPrice() { return price; }
    public Integer getStatus() { return status; }
}
