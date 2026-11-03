package com.r2s.masterclass;

import com.r2s.masterclass.domain.Product;
import com.r2s.masterclass.dto.InventoryAuditLogResponseDTO;
import com.r2s.masterclass.repository.InventoryAuditLogRepository;
import com.r2s.masterclass.repository.ProductRepository;
import com.r2s.masterclass.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Module 1: kiểm chứng audit log trên database thật (H2) và tính nhất quán transaction. */
@SpringBootTest
@ActiveProfiles("h2")
class InventoryAuditLogIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryAuditLogRepository auditLogRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long productId;

    @BeforeEach
    void setUp() {
        productId = productRepository.save(Product.builder()
                .name("Audit Test Product")
                .sku("AUDIT-" + UUID.randomUUID())
                .price(BigDecimal.valueOf(1000))
                .stockQuantity(10)
                .build()).getId();
    }

    @Test
    @DisplayName("Trừ 2 từ tồn kho 10 -> có log DEDUCT previousStock=10, newStock=8")
    void deductStock_ShouldWriteDeductAuditLog() {
        inventoryService.deductStock(productId, 2);

        List<InventoryAuditLogResponseDTO> logs = inventoryService.getAuditLogs(productId);

        assertThat(logs).singleElement().satisfies(log -> {
            assertThat(log.getAction()).isEqualTo("DEDUCT");
            assertThat(log.getChangedQuantity()).isEqualTo(2);
            assertThat(log.getPreviousStock()).isEqualTo(10);
            assertThat(log.getNewStock()).isEqualTo(8);
        });
    }

    @Test
    @DisplayName("Trap 1: Transaction bên ngoài rollback thì cả trừ kho lẫn audit log đều rollback")
    void deductStock_WhenOuterTransactionRollsBack_ShouldRollbackAuditLogToo() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            inventoryService.deductStock(productId, 3);
            // Giả lập lỗi thanh toán xảy ra SAU khi đã trừ kho
            throw new IllegalStateException("Payment gateway failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(auditLogRepository.findByProductIdOrderByCreatedAtDesc(productId)).isEmpty();
    }
}
