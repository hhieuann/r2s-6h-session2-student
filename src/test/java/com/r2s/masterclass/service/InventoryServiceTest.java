package com.r2s.masterclass.service;

import com.r2s.masterclass.domain.Product;
import com.r2s.masterclass.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
