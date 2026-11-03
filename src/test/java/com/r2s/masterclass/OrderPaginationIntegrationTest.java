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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** Module 2: phân trang dưới DB + @BatchSize, không phân trang trên RAM, số câu SQL cố định. */
@SpringBootTest
@ActiveProfiles("h2")
@ExtendWith(OutputCaptureExtension.class)
class OrderPaginationIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        orderService.seedOrders(120);
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    private long countStatementsForPage(int size) {
        statistics.clear();
        Page<OrderResponseDTO> page = orderService.getOrdersPaged(OrderStatus.PAID, PageRequest.of(0, size));
        assertThat(page.getContent()).hasSize(size)
                .allSatisfy(o -> assertThat(o.getItems()).isNotEmpty());
        return statistics.getPrepareStatementCount();
    }

    @Test
    @DisplayName("Trang 5 đơn: đúng metadata, phân trang dưới DB, không có cảnh báo HHH000104")
    void getOrdersPaged_ShouldPaginateInDatabaseWithoutInMemoryWarning(CapturedOutput output) {
        statistics.clear();
        Page<OrderResponseDTO> page = orderService.getOrdersPaged(OrderStatus.PAID, PageRequest.of(0, 5));

        assertThat(page.getContent()).hasSize(5);
        assertThat(page.getNumber()).isZero();
        assertThat(page.getSize()).isEqualTo(5);
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(120);
        assertThat(page.getTotalPages()).isGreaterThanOrEqualTo(24);

        // orders (có LIMIT) + count + items IN (...) + products IN (...)
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);
        assertThat(output).doesNotContain("HHH000104");
        // H2Dialect sinh "offset ? rows fetch first ? rows only", PostgreSQL sinh "limit ? offset ?"
        assertThat(output.getOut()).containsAnyOf("offset", "limit");
        assertThat(output.getOut()).contains("order_id in");
    }

    @Test
    @DisplayName("Số câu SQL không tăng theo kích thước trang (O(1)), trang 50 đơn vẫn bằng trang 5 đơn")
    void getOrdersPaged_StatementCountShouldNotGrowWithPageSize() {
        long smallPage = countStatementsForPage(5);
        long largePage = countStatementsForPage(50);

        assertThat(largePage).isEqualTo(smallPage);
    }
}
