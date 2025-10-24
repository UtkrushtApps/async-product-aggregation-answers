package com.example.aggregation;

import com.example.aggregation.models.Inventory;
import com.example.aggregation.models.ProductDetails;
import com.example.aggregation.models.ProductView;
import com.example.aggregation.models.Recommendations;
import com.example.aggregation.models.ServiceStatus;
import com.example.aggregation.services.InventoryService;
import com.example.aggregation.services.ProductDetailsService;
import com.example.aggregation.services.RecommendationService;

import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProductAggregationService {
    private static final Logger logger = Logger.getLogger(ProductAggregationService.class.getName());
    // Default: don't allow unbounded threads, room for typical async concurrency
    private static final ExecutorService executor = new ThreadPoolExecutor(
        8, // core
        32, // max
        60, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(128),
        r -> new Thread(r, "ProductAggWorker-" + System.nanoTime())
    );

    private static final long SERVICE_TIMEOUT_MS = 700; // milliseconds for each service

    private final ProductDetailsService productDetailsService;
    private final InventoryService inventoryService;
    private final RecommendationService recommendationService;

    public ProductAggregationService(ProductDetailsService detailsService,
                                     InventoryService inventoryService,
                                     RecommendationService recommendationService) {
        this.productDetailsService = detailsService;
        this.inventoryService = inventoryService;
        this.recommendationService = recommendationService;
    }

    public ProductView getAggregatedProductView(String productId, String userId) {
        CompletableFuture<Result<ProductDetails>> detailsFuture = supplyAsyncWithResult(
            () -> productDetailsService.getProductDetails(productId),
            "ProductDetailsService",
            SERVICE_TIMEOUT_MS
        );
        CompletableFuture<Result<Inventory>> inventoryFuture = supplyAsyncWithResult(
            () -> inventoryService.getInventory(productId),
            "InventoryService",
            SERVICE_TIMEOUT_MS
        );
        CompletableFuture<Result<Recommendations>> recommendationsFuture = supplyAsyncWithResult(
            () -> recommendationService.getRecommendations(productId, userId),
            "RecommendationService",
            SERVICE_TIMEOUT_MS
        );

        // Wait for all, but never block I/O thread if possible
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
                detailsFuture, inventoryFuture, recommendationsFuture
        );
        try {
            allOf.get(SERVICE_TIMEOUT_MS + 300, TimeUnit.MILLISECONDS); // give a little buffer
        } catch (Exception e) {
            logger.log(Level.WARNING, "Aggregate wait timed out or interrupted", e);
        }

        Result<ProductDetails> detailsResult = detailsFuture.getNow(Result.failure("timeout"), null);
        Result<Inventory> inventoryResult = inventoryFuture.getNow(Result.failure("timeout"), null);
        Result<Recommendations> recommendationsResult = recommendationsFuture.getNow(Result.failure("timeout"), null);

        return ProductView.builder()
                .productDetails(detailsResult.value)
                .inventory(inventoryResult.value)
                .recommendations(recommendationsResult.value)
                .status(ServiceStatus.builder()
                        .productDetailsStatus(detailsResult.status)
                        .inventoryStatus(inventoryResult.status)
                        .recommendationStatus(recommendationsResult.status)
                        .build())
                .build();
    }

    // Wraps Supplier with async + result, timeouts and error capture
    private static <T> CompletableFuture<Result<T>> supplyAsyncWithResult(Supplier<T> supplier, String serviceName, long timeoutMs) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                T value = supplier.get();
                return Result.success(value);
            } catch (Exception e) {
                logger.log(Level.WARNING, serviceName + " failed: " + e.getMessage(), e);
                return Result.<T>failure(e.getMessage());
            }
        }, executor)
        .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .exceptionally(ex -> {
            String msg = ex.getMessage();
            if (ex instanceof TimeoutException) {
                msg = "Timeout";
            } else if (ex.getCause() != null && ex.getCause() instanceof TimeoutException) {
                msg = "Timeout";
            }
            logger.log(Level.WARNING, serviceName + " async error: " + msg, ex);
            return Result.<T>failure(msg);
        });
    }

    private static class Result<T> {
        final T value;
        final String status;
        private Result(T value, String status) {
            this.value = value;
            this.status = status;
        }
        static <T> Result<T> success(T value) { return new Result<>(value, "OK"); }
        static <T> Result<T> failure(String msg) { return new Result<>(null, msg); }
    }
}
