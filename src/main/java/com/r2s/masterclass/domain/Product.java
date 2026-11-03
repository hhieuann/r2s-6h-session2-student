package com.r2s.masterclass.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
// Module 2: các Product proxy (lazy từ OrderItem) cũng được nạp gộp bằng "WHERE id IN (...)"
@BatchSize(size = 50)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stockQuantity;

    // Module 3: Optimistic Locking. Mỗi lần UPDATE thành công version tăng 1;
    // nếu version trong DB đã bị luồng khác đổi -> ObjectOptimisticLockingFailureException.
    // "default 0" để ddl-auto=update thêm cột được cho các dòng đã có sẵn trong PostgreSQL.
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private Long version;
}
