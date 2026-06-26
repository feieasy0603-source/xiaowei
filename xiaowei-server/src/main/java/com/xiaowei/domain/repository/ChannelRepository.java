package com.xiaowei.domain.repository;

import com.xiaowei.domain.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {

    /** dCode 字段不能以 findByDCode 解析（会误识别为 DCode），用显式 JPQL */
    @Query("SELECT c FROM Channel c WHERE c.dCode = :dCode AND c.enabled = true")
    Optional<Channel> findActiveByDCode(@Param("dCode") String dCode);
}
