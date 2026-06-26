package com.xiaowei.domain.repository;

import com.xiaowei.domain.entity.AiModelTokenStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AiModelTokenStatRepository extends JpaRepository<AiModelTokenStat, String> {

    List<AiModelTokenStat> findAllByOrderByTotalTokensDesc();

    @Query("""
            SELECT COALESCE(SUM(s.promptTokens), 0),
                   COALESCE(SUM(s.completionTokens), 0),
                   COALESCE(SUM(s.totalTokens), 0),
                   COALESCE(SUM(s.requestCount), 0),
                   COUNT(s)
            FROM AiModelTokenStat s
            """)
    Object[] aggregateTotals();
}
