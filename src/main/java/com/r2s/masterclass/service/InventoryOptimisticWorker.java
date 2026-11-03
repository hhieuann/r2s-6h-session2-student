package com.r2s.masterclass.service;

import com.r2s.masterclass.domain.Product;
import com.r2s.masterclass.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Module 3 (Cách B): một lần thử trừ kho theo Optimistic Lock.
 * Tách thành bean riêng để tránh bẫy Spring AOP Self-Invocation: InventoryService gọi qua proxy
 * của bean này nên mỗi lần retry là 1 transaction MỚI hoàn toàn (REQUIRES_NEW),
 * không bị dính cờ rollback-only của lần thử trước.
 */
@Component
@RequiredArgsConstructor
public class InventoryOptimisticWorker {

    private final ProductRepository productRepository;
    private final InventoryAuditRecorder auditRecorder;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deductSingle(Long productId, Integer quantity) {
        // Đọc KHÔNG khóa: xung đột chỉ bị phát hiện lúc UPDATE ... WHERE version = ?
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + productId));

        if (product.getStockQuantity() < quantity) {
            return false;
        }

        int previousStock = product.getStockQuantity();
        product.setStockQuantity(previousStock - quantity);
        productRepository.save(product);

        auditRecorder.record(productId, InventoryAuditRecorder.ACTION_DEDUCT,
                quantity, previousStock, product.getStockQuantity());
        return true;
    }
}
