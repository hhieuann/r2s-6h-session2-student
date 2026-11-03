package com.r2s.masterclass.repository;

import com.r2s.masterclass.domain.Order;
import com.r2s.masterclass.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * [FIX BUG 1 - N+1 QUERY]
     * Lấy Orders + Items + Product trong DUY NHẤT 1 câu SQL bằng JOIN FETCH.
     * DISTINCT loại bỏ các Order bị lặp lại do phép JOIN với collection items.
     * LEFT JOIN để vẫn trả về Order chưa có item nào.
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.items i " +
           "LEFT JOIN FETCH i.product " +
           "WHERE o.status = :status")
    List<Order> findByStatusWithItemsAndProducts(@Param("status") OrderStatus status);
}
