package com.r2s.masterclass.service;

import com.r2s.masterclass.domain.Product;
import com.r2s.masterclass.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests for InventoryOptimisticWorker")
class InventoryOptimisticWorkerTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryAuditRecorder auditRecorder;

    @InjectMocks
    private InventoryOptimisticWorker worker;

    @Test
    @DisplayName("deductSingle: Đủ hàng thì trừ kho, lưu và ghi audit log")
    void deductSingle_WhenStockIsSufficient_ShouldDeductSaveAndAudit() {
        Product product = Product.builder().id(1L).stockQuantity(10).version(4L).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        boolean result = worker.deductSingle(1L, 3);

        assertThat(result).isTrue();
        assertThat(product.getStockQuantity()).isEqualTo(7);
        verify(productRepository).save(product);
        verify(auditRecorder).record(1L, InventoryAuditRecorder.ACTION_DEDUCT, 3, 10, 7);
        // Optimistic Lock: đọc thường, không được dùng khóa bi quan
        verify(productRepository, never()).findByIdWithPessimisticLock(any());
    }

    @Test
    @DisplayName("deductSingle: Không đủ hàng thì trả về false, không lưu, không ghi log")
    void deductSingle_WhenStockIsInsufficient_ShouldReturnFalse() {
        Product product = Product.builder().id(1L).stockQuantity(1).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        boolean result = worker.deductSingle(1L, 2);

        assertThat(result).isFalse();
        verify(productRepository, never()).save(any());
        verifyNoInteractions(auditRecorder);
    }

    @Test
    @DisplayName("deductSingle: Sản phẩm không tồn tại thì ném IllegalArgumentException")
    void deductSingle_WhenProductNotFound_ShouldThrow() {
        when(productRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> worker.deductSingle(9L, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found with id: 9");
    }
}
