package com.xiaowei.domain.repository;

import com.xiaowei.domain.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPhone(String phone);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    Optional<User> findByReferralCode(String referralCode);

    long countByInvitedByUserId(Long invitedByUserId);

    @Query("""
            SELECT u FROM User u
            WHERE (:phone IS NULL OR :phone = '' OR u.phone LIKE CONCAT('%', :phone, '%'))
              AND (:status IS NULL OR :status = '' OR u.status = :status)
            ORDER BY u.id DESC
            """)
    Page<User> search(@Param("phone") String phone, @Param("status") String status, Pageable pageable);

    @Query("SELECT COALESCE(SUM(u.balance), 0) FROM User u")
    BigDecimal sumBalance();
}
