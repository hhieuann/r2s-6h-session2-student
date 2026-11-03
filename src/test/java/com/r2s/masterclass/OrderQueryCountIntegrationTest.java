package com.r2s.masterclass;

import com.r2s.masterclass.domain.OrderStatus;
import com.r2s.masterclass.dto.OrderResponseDTO;
import com.r2s.masterclass.service.OrderService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bằng chứng nghiệm thu Task 1: đếm số câu SQL thực tế bằng Hibernate Statistics.
 * Dữ liệu mẫu (50 đơn hàng x 2 items) được seed sẵn bởi R2SApplication#initDatabase.
 */
@SpringBootTest
@ActiveProfiles("h2")
class OrderQueryCountIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    @Test
    @DisplayName("Task 1: GET orders?status=PAID chỉ được thực thi DUY NHẤT 1 câu SQL")
    void getAllOrdersWithDetails_ShouldExecuteExactlyOneSqlStatement() {
        // Seed thêm 200 đơn để chứng minh số câu SQL không tăng theo số lượng đơn hàng (O(1))
        orderService.seedOrders(200);
        statistics.clear();

        List<OrderResponseDTO> orders = orderService.getAllOrdersWithDetails(OrderStatus.PAID);

        assertThat(orders).hasSizeGreaterThanOrEqualTo(200);
        assertThat(orders).allSatisfy(o -> assertThat(o.getItems()).isNotEmpty());
        assertThat(statistics.getPrepareStatementCount())
                .as("Số câu SQL thực thi khi lấy %d đơn hàng", orders.size())
                .isEqualTo(1);
    }
}
