package com.r2s.masterclass;

import com.r2s.masterclass.domain.Product;
import com.r2s.masterclass.repository.OrderRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("h2")
public class StudentInventoryConcurrencyTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    private Long testProductId;
    private static final int INITIAL_STOCK = 10;
    private static final int CONCURRENT_USERS = 50;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();

        Product product = Product.builder()
                .name("Flash Sale iPhone 15 Pro")
                .sku("FLASH-TEST-" + UUID.randomUUID())
                .price(BigDecimal.valueOf(25000000))
                .stockQuantity(INITIAL_STOCK)
                .build();

        Product saved = productRepository.save(product);
        testProductId = saved.getId();
    }

    @Test
    @DisplayName("Fix BUG 2: 50 luồng đồng thời -> đúng 10 thành công, 40 bị từ chối, tồn kho = 0")
    void testPessimisticLockPreventsNegativeStock() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_USERS);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch finishSignal = new CountDownLatch(CONCURRENT_USERS);

        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger failCounter = new AtomicInteger(0);
        AtomicInteger errorCounter = new AtomicInteger(0);

        for (int i = 0; i < CONCURRENT_USERS; i++) {
            executorService.submit(() -> {
                try {
                    startSignal.await();
                    boolean success = inventoryService.deductStock(testProductId, 1);
                    if (success) {
                        successCounter.incrementAndGet();
                    } else {
                        failCounter.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    // Lỗi bất ngờ (vd: lock timeout) không được nuốt im lặng
                    errorCounter.incrementAndGet();
                } finally {
                    finishSignal.countDown();
                }
            });
        }

        startSignal.countDown();
        finishSignal.await();
        executorService.shutdown();

        Product finalProduct = productRepository.findById(testProductId).orElseThrow();

        System.out.println("==================================================");
        System.out.println("   KẾT QUẢ SAU KHI FIX BUG 2 (PESSIMISTIC LOCK)   ");
        System.out.println("==================================================");
        System.out.println("Tồn kho ban đầu             : " + INITIAL_STOCK);
        System.out.println("Số luồng cùng mua           : " + CONCURRENT_USERS);
        System.out.println("Số đơn báo thành công       : " + successCounter.get());
        System.out.println("Số đơn bị từ chối           : " + failCounter.get());
        System.out.println("Số lỗi bất ngờ              : " + errorCounter.get());
        System.out.println("TỒN KHO THỰC TẾ CUỐI CÙNG   : " + finalProduct.getStockQuantity());
        System.out.println("==================================================");

        assertEquals(0, errorCounter.get(), "Không được có lỗi bất ngờ trong lúc tranh chấp khóa");
        assertEquals(INITIAL_STOCK, successCounter.get(), "Chỉ đúng 10 giao dịch được thành công");
        assertEquals(CONCURRENT_USERS - INITIAL_STOCK, failCounter.get(), "40 giao dịch còn lại phải bị từ chối an toàn");
        assertEquals(0, finalProduct.getStockQuantity(), "Tồn kho cuối cùng phải bằng đúng 0, không được âm");
    }
}
