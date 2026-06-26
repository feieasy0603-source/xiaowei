package com.xiaowei.domain.repository;

import com.xiaowei.domain.entity.ProductPrice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductPriceRepository extends JpaRepository<ProductPrice, Long> {

    List<ProductPrice> findByProductIdOrderByWordCountAsc(String productId);

    @Query("""
            SELECT p FROM ProductPrice p
            WHERE p.productId = :productId
              AND (p.wordCount IS NULL OR p.wordCount <= :wordCount)
              AND (p.degree IS NULL OR p.degree = '' OR :degree = '' OR p.degree = :degree)
              AND (p.modelType IS NULL OR p.modelType = '' OR :model = '' OR p.modelType = :model)
            ORDER BY COALESCE(p.wordCount, 0) DESC
            """)
    List<ProductPrice> findBestQuoteTier(
            @Param("productId") String productId,
            @Param("degree") String degree,
            @Param("model") String model,
            @Param("wordCount") int wordCount,
            Pageable pageable);
}
