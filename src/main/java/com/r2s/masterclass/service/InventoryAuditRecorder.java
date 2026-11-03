package com.r2s.masterclass.service;

import com.r2s.masterclass.domain.InventoryAuditLog;
import com.r2s.masterclass.repository.InventoryAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Ghi vết kiểm toán biến động kho.
 * Cố ý KHÔNG dùng @Async hay REQUIRES_NEW: bản ghi log tham gia transaction của hàm gọi,
 * nên nếu thao tác kho bị rollback thì log cũng rollback theo (tránh lệch số liệu).
 */
@Component
@RequiredArgsConstructor
public class InventoryAuditRecorder {

    public static final String ACTION_DEDUCT = "DEDUCT";
    public static final String ACTION_RESTOCK = "RESTOCK";

    private final InventoryAuditLogRepository auditLogRepository;

    public void record(Long productId, String action, int changedQuantity, int previousStock, int newStock) {
        auditLogRepository.save(InventoryAuditLog.builder()
                .productId(productId)
                .action(action)
                .changedQuantity(changedQuantity)
                .previousStock(previousStock)
                .newStock(newStock)
                .createdAt(LocalDateTime.now())
                .build());
    }
}
