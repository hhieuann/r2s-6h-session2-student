package com.r2s.masterclass.repository;

import com.r2s.masterclass.domain.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySku(String sku);

    /**
     * [FIX BUG 2 - RACE CONDITION]
     * Khóa bi quan: sinh ra SELECT ... FOR UPDATE, khóa độc quyền dòng sản phẩm
     * cho tới khi transaction commit/rollback. Các luồng khác phải xếp hàng chờ,
     * nên mỗi luồng luôn đọc được tồn kho mới nhất trước khi kiểm tra và trừ.
     * Bắt buộc gọi bên trong một @Transactional.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithPessimisticLock(@Param("id") Long id);
}
