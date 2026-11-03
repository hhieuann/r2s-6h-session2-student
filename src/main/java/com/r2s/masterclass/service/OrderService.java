package com.r2s.masterclass.service;

import com.r2s.masterclass.domain.Order;
import com.r2s.masterclass.domain.OrderItem;
import com.r2s.masterclass.domain.OrderStatus;
import com.r2s.masterclass.domain.Product;
import com.r2s.masterclass.dto.OrderItemResponseDTO;
import com.r2s.masterclass.dto.OrderResponseDTO;
import com.r2s.masterclass.repository.OrderRepository;
import com.r2s.masterclass.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    /**
     * Helper method to generate bulk orders for N+1 SQL storm demo
     */
    @Transactional
    public int seedOrders(int count) {
        log.info("Generating {} demo orders for N+1 SQL storm demo...", count);
        Product sampleProduct = productRepository.findAll().stream().findFirst()
                .orElseGet(() -> productRepository.save(Product.builder()
                        .name("Demo Bulk Product")
                        .sku("BULK-" + System.currentTimeMillis())
                        .price(BigDecimal.valueOf(100000))
                        .stockQuantity(5000)
                        .build()));

        List<Order> newOrders = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Order order = Order.builder()
                    .orderCode("BULK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .customerEmail("stress" + i + "@r2s.edu.vn")
                    .status(OrderStatus.PAID)
                    .totalAmount(BigDecimal.valueOf(100000))
                    .createdAt(LocalDateTime.now())
                    .items(new ArrayList<>())
                    .build();

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(sampleProduct)
                    .quantity(1)
                    .unitPrice(sampleProduct.getPrice())
                    .build();

            order.getItems().add(item);
            newOrders.add(order);
        }

        orderRepository.saveAll(newOrders);
        log.info("Successfully seeded {} orders in DB.", newOrders.size());
        return newOrders.size();
    }

    /**
     * [FIX BUG 1 - N+1 QUERY]
     * Trước: findByStatus() + lazy load items/product trong vòng lặp -> 1 + N + N*M câu SQL.
     * Sau: findByStatusWithItemsAndProducts() dùng JOIN FETCH -> chỉ 1 câu SQL duy nhất,
     * vòng lặp bên dưới chỉ đọc dữ liệu đã nằm sẵn trong bộ nhớ.
     */
    @Transactional(readOnly = true)
    public List<OrderResponseDTO> getAllOrdersWithDetails(OrderStatus status) {
        log.info("Fetching orders with status: {}", status);

        // Items và Product đã được JOIN FETCH nên toDto() không bắn thêm câu SQL nào
        List<OrderResponseDTO> results = orderRepository.findByStatusWithItemsAndProducts(status).stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        log.info("Finished processing {} orders into DTOs", results.size());
        return results;
    }

    /**
     * Module 2: phân trang an toàn khi cần lấy kèm collection items.
     * Câu 1: SELECT orders ... LIMIT/OFFSET (phân trang dưới DB, không kéo cả bảng về RAM).
     * Câu 2: @BatchSize gom items của cả trang bằng "WHERE order_id IN (...)".
     * Câu 3: @BatchSize trên Product gom các sản phẩm bằng "WHERE id IN (...)".
     */
    @Transactional(readOnly = true)
    public Page<OrderResponseDTO> getOrdersPaged(OrderStatus status, Pageable pageable) {
        return orderRepository.findOrdersPaged(status, pageable).map(this::toDto);
    }

    private OrderResponseDTO toDto(Order order) {
        List<OrderItemResponseDTO> itemDTOs = order.getItems().stream()
                .map(item -> OrderItemResponseDTO.builder()
                        .itemId(item.getId())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .build())
                .collect(Collectors.toList());

        return OrderResponseDTO.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .customerEmail(order.getCustomerEmail())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .items(itemDTOs)
                .build();
    }
}
