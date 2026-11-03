package com.r2s.masterclass.service;

import com.r2s.masterclass.domain.Product;
import com.r2s.masterclass.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * Yêu cầu:
 * 1. Mock ProductRepository để cô lập kiểm thử tầng nghiệp vụ InventoryService.
 * 2. Hoàn thiện 6 test case bên dưới để đạt độ phủ mã nguồn (Code Coverage >= 80%).
 * 3. Chú ý: Sau khi bạn fix Bug 2 (Pessimistic Lock), hãy chuyển stub sang
 *    `findByIdWithPessimisticLock(productId)` để bài test khớp với code mới!
 * ==============================================================================
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests for InventoryService (Student Starter)")
class InventoryServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    @DisplayName("deductStock: Trừ kho thành công khi sản phẩm tồn tại và đủ số lượng")
    void deductStock_WhenProductExistsAndStockIsSufficient_ShouldDeductAndReturnTrue() {
        // TODO: [Student] 1. Arrange - Giả lập sản phẩm có tồn kho = 10, yêu cầu trừ = 3
        Long productId = 1L;
        Product product = Product.builder()
                .id(productId)
                .name("iPhone 15 Pro")
                .sku("IP15P")
                .price(BigDecimal.valueOf(25000000))
                .stockQuantity(10)
                .build();

        // Chú ý: Code ban đầu dùng findById. Sau khi bạn fix Bug 2, đổi sang findByIdWithPessimisticLock!
        when(productRepository.findByIdWithPessimisticLock(productId)).thenReturn(Optional.of(product));

        // TODO: [Student] 2. Act
        boolean result = inventoryService.deductStock(productId, 3);

        // TODO: [Student] 3. Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("deductStock: Từ chối trừ kho và trả về false khi tồn kho không đủ (Insufficient Stock)")
    void deductStock_WhenStockIsInsufficient_ShouldNotDeductAndReturnFalse() {
        // TODO: [Student] Arrange - Giả lập tồn kho = 2, yêu cầu mua = 5
        Long productId = 1L;
        Product product = Product.builder()
                .id(productId)
                .name("AirPods Pro 2")
                .stockQuantity(2)
                .build();

        when(productRepository.findByIdWithPessimisticLock(productId)).thenReturn(Optional.of(product));

        boolean result = inventoryService.deductStock(productId, 5);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("deductStock: Ném ngoại lệ IllegalArgumentException khi không tìm thấy sản phẩm")
    void deductStock_WhenProductNotFound_ShouldThrowIllegalArgumentException() {
        // TODO: [Student] Mock repository trả về Optional.empty() và kiểm tra ngoại lệ IllegalArgumentException
        Long invalidId = 999L;
        when(productRepository.findByIdWithPessimisticLock(invalidId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.deductStock(invalidId, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found with id: 999");
    }

    @Test
    @DisplayName("getProduct: Lấy thông tin sản phẩm thành công")
    void getProduct_WhenProductExists_ShouldReturnProduct() {
        // TODO: [Student] Mock findById trả về sản phẩm hợp lệ và assert kết quả
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
        // TODO: [Student] Mock findById trả về Optional.empty() và assert ném IllegalArgumentException
        Long nonExistentId = 404L;
        when(productRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.getProduct(nonExistentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found with id: 404");
    }

    @Test
    @DisplayName("resetStock: Cập nhật số lượng tồn kho mới và lưu thành công")
    void resetStock_WhenCalled_ShouldUpdateStockAndSave() {
        // TODO: [Student] Mock getProduct / findById và productRepository.save(...)
        Long productId = 1L;
        Product product = Product.builder().id(productId).stockQuantity(5).build();
        when(productRepository.findByIdWithPessimisticLock(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product updated = inventoryService.resetStock(productId, 100);

        assertThat(updated.getStockQuantity()).isEqualTo(100);
        verify(productRepository, times(1)).save(product);
    }
}
