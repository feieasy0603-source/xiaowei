package com.xiaowei.domain.repository;

import com.xiaowei.domain.entity.LiteratureRef;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LiteratureRefRepository extends JpaRepository<LiteratureRef, Long> {

    @Query("SELECT l FROM LiteratureRef l WHERE l.enabled = true AND " +
           "(LOWER(l.title) LIKE LOWER(CONCAT('%', :kw, '%')) OR LOWER(l.keywords) LIKE LOWER(CONCAT('%', :kw, '%')) " +
           "OR LOWER(l.authors) LIKE LOWER(CONCAT('%', :kw, '%'))) ORDER BY l.year DESC, l.id DESC")
    List<LiteratureRef> search(@Param("kw") String keyword, org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT l FROM LiteratureRef l
            WHERE (:keyword IS NULL OR :keyword = '' OR LOWER(l.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(l.keywords) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(l.authors) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:enabled IS NULL OR l.enabled = :enabled)
            ORDER BY l.id DESC
            """)
    List<LiteratureRef> adminSearch(
            @Param("keyword") String keyword,
            @Param("enabled") Boolean enabled);

    @Query("""
            SELECT l FROM LiteratureRef l
            WHERE (:keyword IS NULL OR :keyword = '' OR LOWER(l.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(l.keywords) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(l.authors) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:enabled IS NULL OR l.enabled = :enabled)
            ORDER BY l.id DESC
            """)
    Page<LiteratureRef> adminSearchPage(
            @Param("keyword") String keyword,
            @Param("enabled") Boolean enabled,
            Pageable pageable);
}
