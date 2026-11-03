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

    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    public ResponseEntity<?> getProductStock(@PathVariable Long productId) {
        Product product = inventoryService.getProduct(productId);
        return ResponseEntity.ok(Map.of(
                "productId", product.getId(),
                "name", product.getName(),
                "sku", product.getSku(),
                "price", product.getPrice(),
                "stockQuantity", product.getStockQuantity()
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

    @PostMapping("/deduct")
    public ResponseEntity<?> deductStock(@Valid @RequestBody StockDeductRequestDTO request) {
        boolean success = inventoryService.deductStock(request.getProductId(), request.getQuantity());
        if (success) {
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Stock deducted successfully",
                    "productId", request.getProductId(),
                    "quantityDeducted", request.getQuantity()
            ));
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "status", "FAILED",
                    "message", "Insufficient stock available"
            ));
        }
    }
}
