package com.r2s.masterclass.repository;

import com.r2s.masterclass.domain.InventoryAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryAuditLogRepository extends JpaRepository<InventoryAuditLog, Long> {

    List<InventoryAuditLog> findByProductIdOrderByCreatedAtDesc(Long productId);
}
