package com.r2s.masterclass.service;

import com.r2s.masterclass.domain.InventoryAuditLog;
import com.r2s.masterclass.repository.InventoryAuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests for InventoryAuditRecorder")
class InventoryAuditRecorderTest {

    @Mock
    private InventoryAuditLogRepository auditLogRepository;

    @InjectMocks
    private InventoryAuditRecorder auditRecorder;

    @Test
    @DisplayName("record: Lưu đầy đủ thông tin biến động kho kèm thời điểm ghi nhận")
    void record_ShouldSaveAuditLogWithAllFields() {
        auditRecorder.record(1L, InventoryAuditRecorder.ACTION_DEDUCT, 2, 10, 8);

        ArgumentCaptor<InventoryAuditLog> captor = ArgumentCaptor.forClass(InventoryAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        InventoryAuditLog saved = captor.getValue();
        assertThat(saved.getProductId()).isEqualTo(1L);
        assertThat(saved.getAction()).isEqualTo("DEDUCT");
        assertThat(saved.getChangedQuantity()).isEqualTo(2);
        assertThat(saved.getPreviousStock()).isEqualTo(10);
        assertThat(saved.getNewStock()).isEqualTo(8);
        assertThat(saved.getCreatedAt()).isNotNull();
    }
}
