package com.xiaowei.domain.repository;

import com.xiaowei.domain.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {
    Optional<AdminUser> findByUsernameAndEnabledTrue(String username);
}
