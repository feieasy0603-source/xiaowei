package com.xiaowei.domain.repository;

import com.xiaowei.domain.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNo(String orderNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.orderNo = :orderNo")
    Optional<Order> findByOrderNoForUpdate(@Param("orderNo") String orderNo);

    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<Order> findFirstByUserIdAndProductIdAndPaperIdAndPayStatusOrderByCreatedAtDesc(
            Long userId, String productId, String paperId, String payStatus);

    Optional<Order> findFirstByUserIdAndProductIdAndPaperIdIsNullAndPayStatusOrderByCreatedAtDesc(
            Long userId, String productId, String payStatus);

    Page<Order> findByPayStatusOrderByCreatedAtDesc(String payStatus, Pageable pageable);

    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
            SELECT o FROM Order o
            WHERE (:payStatus IS NULL OR :payStatus = '' OR o.payStatus = :payStatus)
              AND (:orderNo IS NULL OR :orderNo = '' OR o.orderNo LIKE CONCAT('%', :orderNo, '%'))
              AND (:userId IS NULL OR o.userId = :userId)
              AND (:createdFrom IS NULL OR o.createdAt >= :createdFrom)
              AND (:createdTo IS NULL OR o.createdAt <= :createdTo)
            ORDER BY o.createdAt DESC
            """)
    Page<Order> search(
            @Param("payStatus") String payStatus,
            @Param("orderNo") String orderNo,
            @Param("userId") Long userId,
            @Param("createdFrom") Instant createdFrom,
            @Param("createdTo") Instant createdTo,
            Pageable pageable);

    @Query("SELECT COALESCE(SUM(o.amount), 0) FROM Order o WHERE o.payStatus = 'paid'")
    BigDecimal sumPaidAmount();

    long countByPayStatus(String payStatus);

    @Query("""
            SELECT o.channelId, COUNT(o), COALESCE(SUM(o.amount), 0)
            FROM Order o
            WHERE o.payStatus = 'paid' AND o.channelId IS NOT NULL
            GROUP BY o.channelId
            """)
    List<Object[]> aggregatePaidByChannel();
}
