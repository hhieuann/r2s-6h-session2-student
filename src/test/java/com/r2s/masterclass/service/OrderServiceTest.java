package com.r2s.masterclass.service;

import com.r2s.masterclass.domain.Order;
import com.r2s.masterclass.domain.OrderItem;
import com.r2s.masterclass.domain.OrderStatus;
import com.r2s.masterclass.domain.Product;
import com.r2s.masterclass.dto.OrderItemResponseDTO;
import com.r2s.masterclass.dto.OrderResponseDTO;
import com.r2s.masterclass.repository.OrderRepository;
import com.r2s.masterclass.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * Mock OrderRepository và ProductRepository để cô lập hoàn toàn tầng nghiệp vụ OrderService.
 * Chạy `mvn clean test jacoco:report` rồi mở `target/site/jacoco/index.html` để xem coverage.
 * ==============================================================================
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests for OrderService")
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
        Product sampleProduct = Product.builder()
                .id(101L)
                .name("MacBook Pro M3")
                .sku("MBP-M3")
                .price(BigDecimal.valueOf(45000000))
                .stockQuantity(20)
                .build();

        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 3, 9, 0);
        Order sampleOrder = Order.builder()
                .id(1L)
                .orderCode("ORD-ABC12345")
                .customerEmail("student@r2s.edu.vn")
                .status(OrderStatus.PAID)
                .totalAmount(BigDecimal.valueOf(45000000))
                .createdAt(createdAt)
                .items(new ArrayList<>())
                .build();

        OrderItem sampleItem = OrderItem.builder()
                .id(10L)
                .order(sampleOrder)
                .product(sampleProduct)
                .quantity(2)
                .unitPrice(sampleProduct.getPrice())
                .build();

        sampleOrder.getItems().add(sampleItem);

        when(orderRepository.findByStatusWithItemsAndProducts(OrderStatus.PAID))
                .thenReturn(List.of(sampleOrder));

        List<OrderResponseDTO> result = orderService.getAllOrdersWithDetails(OrderStatus.PAID);

        assertThat(result).hasSize(1);
        OrderResponseDTO dto = result.get(0);
        assertThat(dto.getOrderId()).isEqualTo(1L);
        assertThat(dto.getOrderCode()).isEqualTo("ORD-ABC12345");
        assertThat(dto.getCustomerEmail()).isEqualTo("student@r2s.edu.vn");
        assertThat(dto.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(dto.getTotalAmount()).isEqualByComparingTo("45000000");
        assertThat(dto.getCreatedAt()).isEqualTo(createdAt);

        assertThat(dto.getItems()).hasSize(1);
        OrderItemResponseDTO itemDto = dto.getItems().get(0);
        assertThat(itemDto.getItemId()).isEqualTo(10L);
        assertThat(itemDto.getProductId()).isEqualTo(101L);
        assertThat(itemDto.getProductName()).isEqualTo("MacBook Pro M3");
        assertThat(itemDto.getQuantity()).isEqualTo(2);
        assertThat(itemDto.getUnitPrice()).isEqualByComparingTo("45000000");

        // Chỉ được gọi đúng hàm JOIN FETCH, không còn hàm cũ gây N+1
        verify(orderRepository, times(1)).findByStatusWithItemsAndProducts(OrderStatus.PAID);
        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    @DisplayName("getAllOrdersWithDetails: Đơn hàng không có item vẫn được trả về với danh sách items rỗng")
    void getAllOrdersWithDetails_WhenOrderHasNoItems_ShouldReturnEmptyItems() {
        Order emptyOrder = Order.builder()
                .id(2L)
                .orderCode("ORD-EMPTY")
                .customerEmail("empty@r2s.edu.vn")
                .status(OrderStatus.PENDING)
                .items(new ArrayList<>())
                .build();
        when(orderRepository.findByStatusWithItemsAndProducts(OrderStatus.PENDING)).thenReturn(List.of(emptyOrder));

        List<OrderResponseDTO> result = orderService.getAllOrdersWithDetails(OrderStatus.PENDING);

        assertThat(result).singleElement().satisfies(dto -> assertThat(dto.getItems()).isEmpty());
    }

    @Test
    @DisplayName("getAllOrdersWithDetails: Trả về danh sách rỗng khi không có đơn hàng phù hợp")
    void getAllOrdersWithDetails_WhenNoOrdersFound_ShouldReturnEmptyList() {
        when(orderRepository.findByStatusWithItemsAndProducts(OrderStatus.CANCELLED))
                .thenReturn(Collections.emptyList());

        List<OrderResponseDTO> result = orderService.getAllOrdersWithDetails(OrderStatus.CANCELLED);

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("seedOrders: Tạo và lưu danh sách đơn hàng mẫu thành công khi đã có sản phẩm")
    @SuppressWarnings("unchecked")
    void seedOrders_WhenProductExists_ShouldSaveGeneratedOrdersAndReturnCount() {
        Product sampleProduct = Product.builder()
                .id(1L)
                .name("Sample Seed Product")
                .price(BigDecimal.valueOf(100000))
                .build();

        when(productRepository.findAll()).thenReturn(List.of(sampleProduct));
        when(orderRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        int seeded = orderService.seedOrders(5);

        assertThat(seeded).isEqualTo(5);
        ArgumentCaptor<List<Order>> captor = ArgumentCaptor.forClass(List.class);
        verify(orderRepository).saveAll(captor.capture());
        List<Order> savedOrders = captor.getValue();
        assertThat(savedOrders).hasSize(5).allSatisfy(order -> {
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(order.getOrderCode()).startsWith("BULK-");
            assertThat(order.getItems()).singleElement().satisfies(item -> {
                assertThat(item.getProduct()).isSameAs(sampleProduct);
                assertThat(item.getOrder()).isSameAs(order);
                assertThat(item.getUnitPrice()).isEqualByComparingTo("100000");
            });
        });
        // Đã có sản phẩm thì không được tạo sản phẩm mới
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("seedOrders: Khi chưa có sản phẩm mẫu, tự động tạo mới sản phẩm và lưu đơn hàng")
    void seedOrders_WhenNoProductExists_ShouldCreateNewProductAndSeedOrders() {
        when(productRepository.findAll()).thenReturn(Collections.emptyList());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(99L);
            return p;
        });
        when(orderRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        int seededCount = orderService.seedOrders(3);

        assertThat(seededCount).isEqualTo(3);
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, times(1)).save(productCaptor.capture());
        assertThat(productCaptor.getValue().getName()).isEqualTo("Demo Bulk Product");
        assertThat(productCaptor.getValue().getStockQuantity()).isEqualTo(5000);
        verify(orderRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("seedOrders: Giá trị biên count = 0 thì không tạo đơn nào")
    void seedOrders_WhenCountIsZero_ShouldSaveNothing() {
        when(productRepository.findAll()).thenReturn(List.of(Product.builder().id(1L).price(BigDecimal.ONE).build()));

        int seeded = orderService.seedOrders(0);

        assertThat(seeded).isZero();
        verify(orderRepository).saveAll(Collections.emptyList());
    }
}
