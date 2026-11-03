package com.r2s.masterclass.controller;

import com.r2s.masterclass.domain.Product;
import com.r2s.masterclass.dto.InventoryAuditLogResponseDTO;
import com.r2s.masterclass.dto.StockDeductRequestDTO;
import com.r2s.masterclass.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private static final int OPTIMISTIC_MAX_RETRIES = 3;

    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    public ResponseEntity<?> getProductStock(@PathVariable Long productId) {
        Product product = inventoryService.getProduct(productId);
        return ResponseEntity.ok(Map.of(
                "productId", product.getId(),
                "name", product.getName(),
                "sku", product.getSku(),
                "price", product.getPrice(),
                "stockQuantity", product.getStockQuantity(),
                "version", product.getVersion()
        ));
    }

    @GetMapping("/{productId}/audit-logs")
    public ResponseEntity<List<InventoryAuditLogResponseDTO>> getAuditLogs(@PathVariable Long productId) {
        return ResponseEntity.ok(inventoryService.getAuditLogs(productId));
    }

    @PostMapping("/reset/{productId}")
    public ResponseEntity<?> resetStock(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "10") Integer stock) {
        Product updated = inventoryService.resetStock(productId, stock);
        return ResponseEntity.ok(Map.of(
                "message", "Stock reset successfully",
                "productId", updated.getId(),
                "currentStock", updated.getStockQuantity()
        ));
    }

    @PostMapping("/deduct-optimistic")
    public ResponseEntity<?> deductStockOptimistic(@Valid @RequestBody StockDeductRequestDTO request) {
        boolean success = inventoryService.deductStockOptimisticWithRetry(
                request.getProductId(), request.getQuantity(), OPTIMISTIC_MAX_RETRIES);
        // Thất bại có thể do hết hàng hoặc xung đột quá số lần retry: client nên thử lại sau
        return toDeductResponse(success, request, "Insufficient stock or too many concurrent updates, please retry");
    }

    @PostMapping("/deduct")
    public ResponseEntity<?> deductStock(@Valid @RequestBody StockDeductRequestDTO request) {
        boolean success = inventoryService.deductStock(request.getProductId(), request.getQuantity());
        return toDeductResponse(success, request, "Insufficient stock available");
    }

    private ResponseEntity<?> toDeductResponse(boolean success, StockDeductRequestDTO request, String failMessage) {
        if (success) {
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Stock deducted successfully",
                    "productId", request.getProductId(),
                    "quantityDeducted", request.getQuantity()
            ));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "status", "FAILED",
                "message", failMessage
        ));
    }
}
