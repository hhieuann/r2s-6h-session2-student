package com.r2s.masterclass.controller;

import com.r2s.masterclass.domain.OrderStatus;
import com.r2s.masterclass.dto.OrderResponseDTO;
import com.r2s.masterclass.repository.OrderRepository;
import com.r2s.masterclass.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final OrderService orderService;
    private final OrderRepository orderRepository;

    @GetMapping
    public ResponseEntity<List<OrderResponseDTO>> getAllOrders(
            @RequestParam(defaultValue = "PAID") OrderStatus status) {
        return ResponseEntity.ok(orderService.getAllOrdersWithDetails(status));
    }

    @GetMapping("/paged")
    public ResponseEntity<Page<OrderResponseDTO>> getOrdersPaged(
            @RequestParam(defaultValue = "PAID") OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        // Chặn page âm và size quá lớn (vd size=1000000) làm mất tác dụng phân trang
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return ResponseEntity.ok(orderService.getOrdersPaged(status, pageable));
    }

    @PostMapping("/seed")
    public ResponseEntity<?> seedOrders(@RequestParam(defaultValue = "1000") int count) {
        int seeded = orderService.seedOrders(count);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Seeded " + seeded + " orders for N+1 SQL storm demo",
                "totalOrdersInDb", orderRepository.count()
        ));
    }
}
