package com.xiaowei.domain.repository;

import com.xiaowei.domain.entity.PaymentRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

    Optional<PaymentRecord> findByTradeNo(String tradeNo);

    @Query("""
            SELECT r FROM PaymentRecord r
            WHERE (:userId IS NULL OR r.userId = :userId)
              AND (:orderId IS NULL OR r.orderId = :orderId)
              AND (:payMethod IS NULL OR :payMethod = '' OR LOWER(r.payMethod) = LOWER(:payMethod))
              AND (:status IS NULL OR :status = '' OR LOWER(r.status) = LOWER(:status))
              AND (:tradeNo IS NULL OR :tradeNo = '' OR r.tradeNo LIKE CONCAT('%', :tradeNo, '%'))
            ORDER BY r.createdAt DESC
            """)
    Page<PaymentRecord> search(
            @Param("userId") Long userId,
            @Param("orderId") Long orderId,
            @Param("payMethod") String payMethod,
            @Param("status") String status,
            @Param("tradeNo") String tradeNo,
            Pageable pageable);
}
