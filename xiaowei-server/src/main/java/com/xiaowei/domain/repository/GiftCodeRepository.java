package com.xiaowei.domain.repository;

import com.xiaowei.domain.entity.GiftCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GiftCodeRepository extends JpaRepository<GiftCode, Long> {

    Optional<GiftCode> findByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GiftCode g WHERE LOWER(g.code) = LOWER(:code)")
    Optional<GiftCode> findByCodeIgnoreCaseForUpdate(@Param("code") String code);
}
