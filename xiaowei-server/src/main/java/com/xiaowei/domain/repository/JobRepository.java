package com.xiaowei.domain.repository;

import com.xiaowei.domain.entity.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {
    Optional<Job> findByJobNo(String jobNo);

    Optional<Job> findByOrderId(Long orderId);

    List<Job> findByOrderIdIn(Collection<Long> orderIds);

    Optional<Job> findFirstByPaperIdAndUserIdAndOrderIdIsNullAndStatusOrderByFinishedAtDesc(
            String paperId, Long userId, String status);

    Optional<Job> findFirstByPaperIdAndUserIdAndOrderIdIsNullOrderByCreatedAtDesc(
            String paperId, Long userId);

    Optional<Job> findFirstByPaperIdAndUserIdAndOrderIdIsNullAndStatusInOrderByCreatedAtDesc(
            String paperId, Long userId, List<String> statuses);

    List<Job> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Job> findByStatusOrderByCreatedAtAsc(String status);

    long countByStatus(String status);

    @Query("""
            SELECT j FROM Job j
            LEFT JOIN User u ON u.id = j.userId
            WHERE (:status IS NULL OR :status = '' OR j.status = :status)
              AND (:taskType IS NULL OR :taskType = '' OR j.taskType = :taskType)
              AND (:productId IS NULL OR :productId = '' OR j.productId = :productId)
              AND (:jobNo IS NULL OR :jobNo = '' OR j.jobNo LIKE CONCAT('%', :jobNo, '%'))
              AND (:userId IS NULL OR j.userId = :userId)
              AND (:userPhone IS NULL OR :userPhone = '' OR u.phone LIKE CONCAT('%', :userPhone, '%'))
              AND (:createdFrom IS NULL OR j.createdAt >= :createdFrom)
              AND (:createdTo IS NULL OR j.createdAt <= :createdTo)
            ORDER BY j.createdAt DESC
            """)
    Page<Job> search(
            @Param("status") String status,
            @Param("taskType") String taskType,
            @Param("productId") String productId,
            @Param("jobNo") String jobNo,
            @Param("userId") Long userId,
            @Param("userPhone") String userPhone,
            @Param("createdFrom") Instant createdFrom,
            @Param("createdTo") Instant createdTo,
            Pageable pageable);

    List<Job> findByCreatedAtGreaterThanEqual(Instant since);

    long countByCreatedAtGreaterThanEqual(Instant since);

    long countByCreatedAtGreaterThanEqualAndStatus(Instant since, String status);

    @Query(value = """
            SELECT AVG(TIMESTAMPDIFF(SECOND, j.created_at, j.finished_at))
            FROM jobs j
            WHERE j.created_at >= :since
              AND j.status = 'success'
              AND j.finished_at IS NOT NULL
            """, nativeQuery = true)
    Double avgSuccessDurationSecondsSince(@Param("since") Instant since);

    @Query(value = """
            SELECT j.error_msg, COUNT(*)
            FROM jobs j
            WHERE j.created_at >= :since
              AND j.status = 'failed'
              AND j.error_msg IS NOT NULL
              AND TRIM(j.error_msg) <> ''
            GROUP BY j.error_msg
            ORDER BY COUNT(*) DESC
            LIMIT 10
            """, nativeQuery = true)
    List<Object[]> topFailureReasonsSince(@Param("since") Instant since);

    @Query("SELECT j.taskType, COUNT(j) FROM Job j GROUP BY j.taskType")
    List<Object[]> countGroupByTaskType();
}
