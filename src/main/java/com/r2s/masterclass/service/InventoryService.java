package com.r2s.masterclass.service;

import com.r2s.masterclass.domain.Product;
import com.r2s.masterclass.dto.InventoryAuditLogResponseDTO;
import com.r2s.masterclass.repository.InventoryAuditLogRepository;
import com.r2s.masterclass.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final ProductRepository productRepository;
    private final InventoryAuditLogRepository auditLogRepository;
    private final InventoryAuditRecorder auditRecorder;

    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + productId));
    }

    // Ghi đè tồn kho cũng phải khóa dòng, tránh đè mất lượt trừ kho đang diễn ra (lost update)
    @Transactional
    public Product resetStock(Long productId, Integer newStock) {
        Product product = productRepository.findByIdWithPessimisticLock(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + productId));
        int previousStock = product.getStockQuantity();
        product.setStockQuantity(newStock);
        Product saved = productRepository.save(product);

        // changedQuantity có dấu: dương là nhập thêm, âm là giảm bớt
        auditRecorder.record(productId, InventoryAuditRecorder.ACTION_RESTOCK,
                newStock - previousStock, previousStock, newStock);
        return saved;
    }

    /** Lịch sử biến động kho của 1 sản phẩm, mới nhất trước. */
    @Transactional(readOnly = true)
    public List<InventoryAuditLogResponseDTO> getAuditLogs(Long productId) {
        return auditLogRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(entry -> InventoryAuditLogResponseDTO.builder()
                        .logId(entry.getId())
                        .productId(entry.getProductId())
                        .action(entry.getAction())
                        .changedQuantity(entry.getChangedQuantity())
                        .previousStock(entry.getPreviousStock())
                        .newStock(entry.getNewStock())
                        .createdAt(entry.getCreatedAt())
                        .build())
                .toList();
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
        int previousStock = product.getStockQuantity();
        product.setStockQuantity(previousStock - quantity);
        productRepository.save(product);

        // 3. Ghi audit log trong cùng transaction: trừ kho rollback thì log cũng rollback
        auditRecorder.record(productId, InventoryAuditRecorder.ACTION_DEDUCT,
                quantity, previousStock, product.getStockQuantity());

        log.info("[Thread: {}] Stock deducted successfully. Remaining: {}",
                Thread.currentThread().getName(), product.getStockQuantity());
        return true;
    }
}
