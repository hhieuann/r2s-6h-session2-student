package com.r2s.masterclass.service;

import com.r2s.masterclass.domain.Product;
import com.r2s.masterclass.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final ProductRepository productRepository;

    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + productId));
    }

    // Ghi đè tồn kho cũng phải khóa dòng, tránh đè mất lượt trừ kho đang diễn ra (lost update)
    @Transactional
    public Product resetStock(Long productId, Integer newStock) {
        Product product = productRepository.findByIdWithPessimisticLock(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + productId));
        product.setStockQuantity(newStock);
        return productRepository.save(product);
    }

    /**
     * [FIX BUG 2 - RACE CONDITION]
     * Trước: kiểm tra tồn kho trên RAM (findById, không khóa) rồi UPDATE trừ thẳng dưới DB
     * -> 50 luồng cùng đọc stock = 10, cùng pass điều kiện, kho bị trừ còn -40.
     * Sau: khóa dòng sản phẩm bằng SELECT ... FOR UPDATE trong cùng 1 transaction,
     * nên "kiểm tra" và "trừ kho" trở thành một thao tác nguyên tử với các luồng khác.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean deductStock(Long productId, Integer quantity) {
        log.info("[Thread: {}] Attempting stock deduction for Product ID: {}, Quantity: {}",
                Thread.currentThread().getName(), productId, quantity);

        // 1. Khóa độc quyền dòng sản phẩm: luồng khác phải chờ tới khi transaction này kết thúc
        Product product = productRepository.findByIdWithPessimisticLock(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + productId));

        if (product.getStockQuantity() < quantity) {
            log.warn("[Thread: {}] Insufficient stock! Available: {}, Requested: {}",
                    Thread.currentThread().getName(), product.getStockQuantity(), quantity);
            return false;
        }

        // Giả lập độ trễ xử lý nghiệp vụ 40ms (thanh toán, gọi gateway, voucher)
        try {
            Thread.sleep(40);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 2. Trừ kho trên entity đang được khóa và lưu lại
        product.setStockQuantity(product.getStockQuantity() - quantity);
        productRepository.save(product);

        log.info("[Thread: {}] Stock deducted successfully. Remaining: {}",
                Thread.currentThread().getName(), product.getStockQuantity());
        return true;
    }
}
