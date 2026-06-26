package com.xiaowei.domain.repository;

import com.xiaowei.domain.entity.Paper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaperRepository extends JpaRepository<Paper, String> {
    List<Paper> findByUserIdOrderByUpdatedAtDesc(Long userId);

    @Query("""
            SELECT p FROM Paper p
            WHERE (:title IS NULL OR :title = '' OR p.title LIKE CONCAT('%', :title, '%'))
              AND (:productId IS NULL OR :productId = '' OR p.productId = :productId)
            ORDER BY p.updatedAt DESC
            """)
    Page<Paper> search(
            @Param("title") String title,
            @Param("productId") String productId,
            Pageable pageable);
}
