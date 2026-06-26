package com.xiaowei.domain.repository;

import com.xiaowei.domain.entity.WalletLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletLogRepository extends JpaRepository<WalletLog, Long> {

    Page<WalletLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
