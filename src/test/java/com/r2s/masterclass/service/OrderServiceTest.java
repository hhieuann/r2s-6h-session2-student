package com.r2s.masterclass.service;

import com.r2s.masterclass.domain.Order;
import com.r2s.masterclass.domain.OrderItem;
import com.r2s.masterclass.domain.OrderStatus;
import com.r2s.masterclass.domain.Product;
import com.r2s.masterclass.dto.OrderResponseDTO;
import com.r2s.masterclass.repository.OrderRepository;
import com.r2s.masterclass.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * ==============================================================================
 * LAB TASK 3: UNIT TEST VỚI JUNIT 5 & MOCKITO (TARGET: COVERAGE >= 80%)
 * ==============================================================================
 * Yêu cầu:
 * 1. Sử dụng @ExtendWith(MockitoExtension.class) để cô lập kiểm thử tầng Service.
 * 2. Mock 2 phụ thuộc: OrderRepository và ProductRepository.
 * 3. Hoàn thiện 4 test case bên dưới để đạt độ phủ mã nguồn (Code Coverage >= 80%).
 * 4. Chạy lệnh: `mvn clean test jacoco:report` và mở file `target/site/jacoco/index.html`
 *    để kiểm tra kết quả coverage!
 * ==============================================================================
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests for OrderService (Student Starter)")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("getAllOrdersWithDetails: Trả về danh sách OrderResponseDTO chính xác khi có đơn hàng")
    void getAllOrdersWithDetails_WhenOrdersExist_ShouldReturnMappedDtosSuccessfully() {
        // TODO: [Student] 1. Arrange - Chuẩn bị dữ liệu mẫu (Sample Product, Order, OrderItem)
        Product sampleProduct = Product.builder()
                .id(101L)
                .name("MacBook Pro M3")
                .sku("MBP-M3")
                .price(BigDecimal.valueOf(45000000))
                .stockQuantity(20)
                .build();

        Order sampleOrder = Order.builder()
                .id(1L)
                .orderCode("ORD-ABC12345")
                .customerEmail("student@r2s.edu.vn")
                .status(OrderStatus.PAID)
                .totalAmount(BigDecimal.valueOf(45000000))
                .createdAt(LocalDateTime.now())
                .items(new ArrayList<>())
                .build();

        OrderItem sampleItem = OrderItem.builder()
                .id(10L)
                .order(sampleOrder)
                .product(sampleProduct)
                .quantity(1)
                .unitPrice(sampleProduct.getPrice())
                .build();

        sampleOrder.getItems().add(sampleItem);

        // TODO: [Student] 2. Mock hành vi của orderRepository
        // Lưu ý: Sau khi bạn fix Bug 1 (JOIN FETCH), hãy stub hàm repository tương ứng:
        when(orderRepository.findByStatusWithItemsAndProducts(OrderStatus.PAID))
                .thenReturn(List.of(sampleOrder));

        // TODO: [Student] 3. Kích hoạt hàm cần test (Act)
        List<OrderResponseDTO> result = orderService.getAllOrdersWithDetails(OrderStatus.PAID);

        // TODO: [Student] 4. Kiểm chứng kết quả (Assert)
        assertThat(result).isNotNull().hasSize(1);
        OrderResponseDTO dto = result.get(0);
        assertThat(dto.getOrderCode()).isEqualTo("ORD-ABC12345");
        assertThat(dto.getCustomerEmail()).isEqualTo("student@r2s.edu.vn");
        assertThat(dto.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("getAllOrdersWithDetails: Trả về danh sách rỗng khi không có đơn hàng phù hợp")
    void getAllOrdersWithDetails_WhenNoOrdersFound_ShouldReturnEmptyList() {
        // TODO: [Student] Mock repository trả về danh sách rỗng và verify kết quả
        when(orderRepository.findByStatusWithItemsAndProducts(OrderStatus.CANCELLED))
                .thenReturn(Collections.emptyList());

        List<OrderResponseDTO> result = orderService.getAllOrdersWithDetails(OrderStatus.CANCELLED);

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("seedOrders: Tạo và lưu danh sách đơn hàng mẫu thành công")
    void seedOrders_WhenCalled_ShouldSaveGeneratedOrdersAndReturnCount() {
        // TODO: [Student] Mock productRepository.findAll() trả về ít nhất 1 sản phẩm
        // và mock orderRepository.saveAll(...) sau đó assert số lượng đơn được tạo
        Product sampleProduct = Product.builder()
                .id(1L)
                .name("Sample Seed Product")
                .price(BigDecimal.valueOf(100000))
                .build();

        when(productRepository.findAll()).thenReturn(List.of(sampleProduct));
        when(orderRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        int seeded = orderService.seedOrders(5);

        assertThat(seeded).isEqualTo(5);
        verify(productRepository, times(1)).findAll();
        verify(orderRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("seedOrders: Khi chưa có sản phẩm mẫu, tự động tạo mới sản phẩm và lưu đơn hàng")
    void seedOrders_WhenNoProductExists_ShouldCreateNewProductAndSeedOrders() {
        // TODO: [Student] Mock trường hợp database rỗng sản phẩm (findAll trả về emptyList)
        // và mock productRepository.save(...) để kiểm tra nhánh logic tạo sản phẩm mặc định
        when(productRepository.findAll()).thenReturn(Collections.emptyList());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(99L);
            return p;
        });
        when(orderRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        int seededCount = orderService.seedOrders(3);

        assertThat(seededCount).isEqualTo(3);
        verify(productRepository, times(1)).save(any(Product.class));
        verify(orderRepository, times(1)).saveAll(anyList());
    }
}
