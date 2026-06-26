package com.xiaowei.domain.repository;

import com.xiaowei.domain.entity.OutlineTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutlineTemplateRepository extends JpaRepository<OutlineTemplate, Long> {

    @Query("SELECT o FROM OutlineTemplate o WHERE o.enabled = true AND " +
           "(LOWER(o.title) LIKE LOWER(CONCAT('%', :title, '%')) OR :title = '')")
    List<OutlineTemplate> searchByTitle(@Param("title") String title);

    @Query("""
            SELECT o FROM OutlineTemplate o
            WHERE (:category IS NULL OR :category = '' OR o.category = :category)
              AND (:degree IS NULL OR :degree = '' OR o.degree = :degree)
              AND (:enabled IS NULL OR o.enabled = :enabled)
            ORDER BY o.id DESC
            """)
    List<OutlineTemplate> adminSearch(
            @Param("category") String category,
            @Param("degree") String degree,
            @Param("enabled") Boolean enabled);

    @Query("""
            SELECT o FROM OutlineTemplate o
            WHERE (:category IS NULL OR :category = '' OR o.category = :category)
              AND (:degree IS NULL OR :degree = '' OR o.degree = :degree)
              AND (:enabled IS NULL OR o.enabled = :enabled)
            ORDER BY o.id DESC
            """)
    Page<OutlineTemplate> adminSearchPage(
            @Param("category") String category,
            @Param("degree") String degree,
            @Param("enabled") Boolean enabled,
            Pageable pageable);
}
