package com.xiaowei.domain.repository;

import com.xiaowei.domain.entity.VipQuotaConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VipQuotaConfigRepository extends JpaRepository<VipQuotaConfig, Long> {

    List<VipQuotaConfig> findAllByOrderByVipLevelAscTaskTypeAsc();

    List<VipQuotaConfig> findByEnabledTrueOrderByVipLevelAscTaskTypeAsc();

    Optional<VipQuotaConfig> findByVipLevelAndTaskTypeAndEnabledTrue(Integer vipLevel, String taskType);
}
