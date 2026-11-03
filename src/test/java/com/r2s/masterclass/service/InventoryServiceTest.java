package com.r2s.masterclass.service;

import com.r2s.masterclass.domain.InventoryAuditLog;
import com.r2s.masterclass.domain.Product;
import com.r2s.masterclass.dto.InventoryAuditLogResponseDTO;
import com.r2s.masterclass.repository.InventoryAuditLogRepository;
import com.r2s.masterclass.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ==============================================================================
 * LAB TASK 3: UNIT TEST VỚI JUNIT 5 & MOCKITO (TARGET: COVERAGE >= 80%)
 * ==============================================================================
 * Mock ProductRepository để cô lập hoàn toàn tầng nghiệp vụ InventoryService khỏi database.
 * Sau khi fix Bug 2, deductStock/resetStock dùng findByIdWithPessimisticLock nên stub theo hàm đó.
 * ==============================================================================
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests for InventoryService")
class InventoryServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryAuditLogRepository auditLogRepository;

    @Mock
    private InventoryAuditRecorder auditRecorder;

    @Mock
    private InventoryOptimisticWorker optimisticWorker;

    private static ObjectOptimisticLockingFailureException versionConflict() {
        return new ObjectOptimisticLockingFailureException(Product.class, 1L);
    }

    @Test
    @DisplayName("deductStockOptimisticWithRetry: Thành công ngay lần đầu thì không retry")
    void deductStockOptimistic_WhenNoConflict_ShouldSucceedOnFirstAttempt() {
        when(optimisticWorker.deductSingle(1L, 1)).thenReturn(true);

        boolean result = inventoryService.deductStockOptimisticWithRetry(1L, 1, 3);

        assertThat(result).isTrue();
        verify(optimisticWorker, times(1)).deductSingle(1L, 1);
    }

    @Test
    @DisplayName("deductStockOptimisticWithRetry: Xung đột version lần đầu, retry lần 2 thành công")
    void deductStockOptimistic_WhenConflictThenSuccess_ShouldRetryAndSucceed() {
        when(optimisticWorker.deductSingle(1L, 1))
                .thenThrow(versionConflict())
                .thenReturn(true);

        boolean result = inventoryService.deductStockOptimisticWithRetry(1L, 1, 3);

        assertThat(result).isTrue();
        verify(optimisticWorker, times(2)).deductSingle(1L, 1);
    }

    @Test
    @DisplayName("deductStockOptimisticWithRetry: Xung đột liên tục thì dừng đúng sau maxRetries lần và trả về false")
    void deductStockOptimistic_WhenConflictPersists_ShouldGiveUpAfterMaxRetries() {
        when(optimisticWorker.deductSingle(1L, 1)).thenThrow(versionConflict());

        boolean result = inventoryService.deductStockOptimisticWithRetry(1L, 1, 3);

        assertThat(result).isFalse();
        verify(optimisticWorker, times(3)).deductSingle(1L, 1);
    }

    @Test
    @DisplayName("deductStockOptimisticWithRetry: Hết hàng thì trả về false ngay, không retry")
    void deductStockOptimistic_WhenInsufficientStock_ShouldReturnFalseWithoutRetry() {
        when(optimisticWorker.deductSingle(1L, 5)).thenReturn(false);

        boolean result = inventoryService.deductStockOptimisticWithRetry(1L, 5, 3);

        assertThat(result).isFalse();
        verify(optimisticWorker, times(1)).deductSingle(1L, 5);
    }

    @Test
    @DisplayName("deductStockOptimisticWithRetry: Bị interrupt trong lúc chờ backoff thì dừng và giữ cờ interrupt")
    void deductStockOptimistic_WhenInterruptedDuringBackoff_ShouldStopAndKeepInterruptFlag() {
        when(optimisticWorker.deductSingle(1L, 1)).thenThrow(versionConflict());

        Thread.currentThread().interrupt();
        boolean result = inventoryService.deductStockOptimisticWithRetry(1L, 1, 3);

        assertThat(result).isFalse();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(optimisticWorker, times(1)).deductSingle(1L, 1);
    }

    @Test
    @DisplayName("deductStockOptimisticWithRetry: maxRetries = 0 thì không thử lần nào")
    void deductStockOptimistic_WhenMaxRetriesIsZero_ShouldNotAttempt() {
        boolean result = inventoryService.deductStockOptimisticWithRetry(1L, 1, 0);

        assertThat(result).isFalse();
        verifyNoInteractions(optimisticWorker);
    }

    @InjectMocks
    private InventoryService inventoryService;

    @AfterEach
    void clearInterruptFlag() {
        // Đảm bảo cờ interrupt không rò sang test khác
        Thread.interrupted();
    }

    private static Product product(Long id, int stock) {
        return Product.builder()
                .id(id)
                .name("iPhone 15 Pro")
                .sku("IP15P")
                .price(BigDecimal.valueOf(25000000))
                .stockQuantity(stock)
                .build();
    }

    @Test
    @DisplayName("deductStock: Trừ kho thành công khi sản phẩm tồn tại và đủ số lượng")
    void deductStock_WhenProductExistsAndStockIsSufficient_ShouldDeductAndReturnTrue() {
        Long productId = 1L;
        when(productRepository.findByIdWithPessimisticLock(productId)).thenReturn(Optional.of(product(productId, 10)));

        boolean result = inventoryService.deductStock(productId, 3);

        assertThat(result).isTrue();
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getStockQuantity()).isEqualTo(7);
        // Phải đọc qua hàm có khóa, không được dùng findById không khóa
        verify(productRepository, never()).findById(any());
        // Module 1: ghi audit log DEDUCT với tồn kho trước/sau chính xác
        verify(auditRecorder).record(productId, InventoryAuditRecorder.ACTION_DEDUCT, 3, 10, 7);
    }

    @Test
    @DisplayName("deductStock: Giá trị biên - mua đúng bằng số tồn kho thì thành công và kho về 0")
    void deductStock_WhenQuantityEqualsStock_ShouldDeductToZero() {
        Long productId = 1L;
        Product product = product(productId, 5);
        when(productRepository.findByIdWithPessimisticLock(productId)).thenReturn(Optional.of(product));

        boolean result = inventoryService.deductStock(productId, 5);

        assertThat(result).isTrue();
        assertThat(product.getStockQuantity()).isZero();
    }

    @Test
    @DisplayName("deductStock: Từ chối trừ kho và trả về false khi tồn kho không đủ (Insufficient Stock)")
    void deductStock_WhenStockIsInsufficient_ShouldNotDeductAndReturnFalse() {
        Long productId = 1L;
        Product product = product(productId, 2);
        when(productRepository.findByIdWithPessimisticLock(productId)).thenReturn(Optional.of(product));

        boolean result = inventoryService.deductStock(productId, 5);

        assertThat(result).isFalse();
        assertThat(product.getStockQuantity()).isEqualTo(2);
        verify(productRepository, never()).save(any());
        // Không trừ kho thì không được ghi audit log
        verifyNoInteractions(auditRecorder);
    }

    @Test
    @DisplayName("deductStock: Ném ngoại lệ IllegalArgumentException khi không tìm thấy sản phẩm")
    void deductStock_WhenProductNotFound_ShouldThrowIllegalArgumentException() {
        Long invalidId = 999L;
        when(productRepository.findByIdWithPessimisticLock(invalidId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.deductStock(invalidId, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found with id: 999");
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("deductStock: Luồng bị interrupt trong lúc xử lý vẫn trừ kho và giữ lại cờ interrupt")
    void deductStock_WhenThreadInterrupted_ShouldRestoreInterruptFlagAndStillDeduct() {
        Long productId = 1L;
        when(productRepository.findByIdWithPessimisticLock(productId)).thenReturn(Optional.of(product(productId, 10)));

        Thread.currentThread().interrupt();
        boolean result = inventoryService.deductStock(productId, 1);

        assertThat(result).isTrue();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("getProduct: Lấy thông tin sản phẩm thành công")
    void getProduct_WhenProductExists_ShouldReturnProduct() {
        Long productId = 10L;
        Product product = Product.builder().id(productId).name("Test Product").stockQuantity(50).build();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        Product result = inventoryService.getProduct(productId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(productId);
        assertThat(result.getName()).isEqualTo("Test Product");
        verify(productRepository, times(1)).findById(productId);
    }

    @Test
    @DisplayName("getProduct: Ném ngoại lệ khi sản phẩm không tồn tại")
    void getProduct_WhenProductNotFound_ShouldThrowException() {
        Long nonExistentId = 404L;
        when(productRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.getProduct(nonExistentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found with id: 404");
    }

    @Test
    @DisplayName("resetStock: Cập nhật số lượng tồn kho mới và lưu thành công")
    void resetStock_WhenCalled_ShouldUpdateStockAndSave() {
        Long productId = 1L;
        Product product = Product.builder().id(productId).stockQuantity(5).build();
        when(productRepository.findByIdWithPessimisticLock(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product updated = inventoryService.resetStock(productId, 100);

        assertThat(updated.getStockQuantity()).isEqualTo(100);
        verify(productRepository, times(1)).save(product);
        verify(auditRecorder).record(productId, InventoryAuditRecorder.ACTION_RESTOCK, 95, 5, 100);
    }

    @Test
    @DisplayName("getAuditLogs: Ánh xạ đúng lịch sử biến động kho sang DTO")
    void getAuditLogs_WhenLogsExist_ShouldMapToDtos() {
        LocalDateTime now = LocalDateTime.now();
        InventoryAuditLog entry = InventoryAuditLog.builder()
                .id(7L).productId(1L).action("DEDUCT")
                .changedQuantity(2).previousStock(10).newStock(8).createdAt(now)
                .build();
        when(auditLogRepository.findByProductIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(entry));

        List<InventoryAuditLogResponseDTO> result = inventoryService.getAuditLogs(1L);

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.getLogId()).isEqualTo(7L);
            assertThat(dto.getProductId()).isEqualTo(1L);
            assertThat(dto.getAction()).isEqualTo("DEDUCT");
            assertThat(dto.getChangedQuantity()).isEqualTo(2);
            assertThat(dto.getPreviousStock()).isEqualTo(10);
            assertThat(dto.getNewStock()).isEqualTo(8);
            assertThat(dto.getCreatedAt()).isEqualTo(now);
        });
    }

    @Test
    @DisplayName("resetStock: Ném ngoại lệ khi sản phẩm không tồn tại")
    void resetStock_WhenProductNotFound_ShouldThrowException() {
        when(productRepository.findByIdWithPessimisticLock(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.resetStock(404L, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found with id: 404");
        verify(productRepository, never()).save(any());
    }
}
