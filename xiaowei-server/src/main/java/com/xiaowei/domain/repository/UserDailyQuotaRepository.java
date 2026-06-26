package com.xiaowei.domain.repository;

import com.xiaowei.domain.entity.UserDailyQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserDailyQuotaRepository extends JpaRepository<UserDailyQuota, Long> {

    Optional<UserDailyQuota> findByUserIdAndUsageDateAndTaskType(Long userId, LocalDate usageDate, String taskType);

    List<UserDailyQuota> findByUserIdAndUsageDate(Long userId, LocalDate usageDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM UserDailyQuota q WHERE q.userId = :userId AND q.usageDate = :usageDate AND q.taskType = :taskType")
    Optional<UserDailyQuota> findForUpdate(
            @Param("userId") Long userId,
            @Param("usageDate") LocalDate usageDate,
            @Param("taskType") String taskType);
}
