package com.r2s.masterclass.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Vết kiểm toán mọi biến động tồn kho. Được ghi trong CÙNG transaction với thao tác
 * trừ/cập nhật kho: nếu thao tác kho rollback thì bản ghi log cũng rollback theo.
 */
@Entity
@Table(name = "inventory_audit_logs",
        indexes = {@Index(name = "idx_audit_product_id", columnList = "product_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    // DEDUCT, RESTOCK
    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private Integer changedQuantity;

    @Column(nullable = false)
    private Integer previousStock;

    @Column(nullable = false)
    private Integer newStock;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
