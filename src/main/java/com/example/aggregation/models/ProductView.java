package com.example.aggregation.models;

public class ProductView {
    private final ProductDetails productDetails;
    private final Inventory inventory;
    private final Recommendations recommendations;
    private final ServiceStatus status;

    private ProductView(Builder builder) {
        this.productDetails = builder.productDetails;
        this.inventory = builder.inventory;
        this.recommendations = builder.recommendations;
        this.status = builder.status;
    }

    public static Builder builder() { return new Builder(); }

    public ProductDetails getProductDetails() { return productDetails; }
    public Inventory getInventory() { return inventory; }
    public Recommendations getRecommendations() { return recommendations; }
    public ServiceStatus getStatus() { return status; }

    public static class Builder {
        private ProductDetails productDetails;
        private Inventory inventory;
        private Recommendations recommendations;
        private ServiceStatus status;

        public Builder productDetails(ProductDetails productDetails) {
            this.productDetails = productDetails; return this;
        }
        public Builder inventory(Inventory inventory) {
            this.inventory = inventory; return this;
        }
        public Builder recommendations(Recommendations recommendations) {
            this.recommendations = recommendations; return this;
        }
        public Builder status(ServiceStatus status) {
            this.status = status; return this;
        }
        public ProductView build() { return new ProductView(this); }
    }
}
