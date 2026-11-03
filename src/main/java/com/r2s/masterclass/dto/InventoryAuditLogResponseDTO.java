package com.r2s.masterclass.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class InventoryAuditLogResponseDTO {
    private Long logId;
    private Long productId;
    private String action;
    private Integer changedQuantity;
    private Integer previousStock;
    private Integer newStock;
    private LocalDateTime createdAt;
}
