package com.xiaowei.domain.repository;

import com.xiaowei.domain.entity.School;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SchoolRepository extends JpaRepository<School, String> {

    List<School> findByEnabledTrueOrderBySortOrderAscNameAsc();
}
