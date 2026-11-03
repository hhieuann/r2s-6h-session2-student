package com.r2s.masterclass;

import com.r2s.masterclass.domain.Product;
import com.r2s.masterclass.repository.ProductRepository;
import com.r2s.masterclass.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Module 3: Optimistic Lock (@Version) + Retry trên database thật (H2). */
@SpringBootTest
@ActiveProfiles("h2")
class OptimisticLockingIntegrationTest {

    private static final int INITIAL_STOCK = 10;
    private static final int CONCURRENT_USERS = 20;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ProductRepository productRepository;

    private Long productId;

    @BeforeEach
    void setUp() {
        productId = productRepository.save(Product.builder()
                .name("Optimistic Test Product")
                .sku("OPT-" + UUID.randomUUID())
                .price(BigDecimal.valueOf(1000))
                .stockQuantity(INITIAL_STOCK)
                .build()).getId();
    }

    @Test
    @DisplayName("Mỗi lần trừ kho thành công, cột version tăng thêm 1 (0 -> 1 -> 2)")
    void deductStockOptimistic_ShouldIncrementVersionOnEachSuccess() {
        assertThat(productRepository.findById(productId).orElseThrow().getVersion()).isZero();

        assertThat(inventoryService.deductStockOptimisticWithRetry(productId, 1, 3)).isTrue();
        assertThat(productRepository.findById(productId).orElseThrow().getVersion()).isEqualTo(1L);

        assertThat(inventoryService.deductStockOptimisticWithRetry(productId, 1, 3)).isTrue();
        Product after = productRepository.findById(productId).orElseThrow();
        assertThat(after.getVersion()).isEqualTo(2L);
        assertThat(after.getStockQuantity()).isEqualTo(INITIAL_STOCK - 2);
    }

    @Test
    @DisplayName("20 luồng tranh mua: không bao giờ âm kho, số đơn thành công khớp đúng số hàng đã trừ")
    void deductStockOptimistic_UnderConcurrency_ShouldNeverOversell() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_USERS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(CONCURRENT_USERS);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();

        for (int i = 0; i < CONCURRENT_USERS; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (inventoryService.deductStockOptimisticWithRetry(productId, 1, 5)) {
                        success.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        Product finalProduct = productRepository.findById(productId).orElseThrow();
        assertThat(errors.get()).isZero();
        assertThat(finalProduct.getStockQuantity()).isGreaterThanOrEqualTo(0);
        // Bất biến: số hàng đã bán = tồn đầu - tồn cuối = số lần UPDATE thành công (= version)
        assertThat(success.get()).isEqualTo(INITIAL_STOCK - finalProduct.getStockQuantity());
        assertThat(finalProduct.getVersion()).isEqualTo((long) success.get());
    }
}
