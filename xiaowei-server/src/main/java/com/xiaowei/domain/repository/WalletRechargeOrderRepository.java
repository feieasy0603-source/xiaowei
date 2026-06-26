package com.xiaowei.domain.repository;

import com.xiaowei.domain.entity.WalletRechargeOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WalletRechargeOrderRepository extends JpaRepository<WalletRechargeOrder, Long> {
    Optional<WalletRechargeOrder> findByOrderNo(String orderNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM WalletRechargeOrder o WHERE o.orderNo = :orderNo")
    Optional<WalletRechargeOrder> findByOrderNoForUpdate(@Param("orderNo") String orderNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM WalletRechargeOrder o WHERE o.id = :id")
    Optional<WalletRechargeOrder> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT o FROM WalletRechargeOrder o
            WHERE (:payStatus IS NULL OR :payStatus = '' OR o.payStatus = :payStatus)
              AND (:orderNo IS NULL OR :orderNo = '' OR o.orderNo LIKE CONCAT('%', :orderNo, '%'))
              AND (:userId IS NULL OR o.userId = :userId)
            ORDER BY o.createdAt DESC
            """)
    Page<WalletRechargeOrder> search(
            @Param("payStatus") String payStatus,
            @Param("orderNo") String orderNo,
            @Param("userId") Long userId,
            Pageable pageable);
}
