package com.xiaowei.domain.repository;

import com.xiaowei.domain.entity.PaperFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaperFileRepository extends JpaRepository<PaperFile, Long> {

    List<PaperFile> findByPaperIdOrderByCreatedAtDesc(String paperId);

    List<PaperFile> findByJobId(Long jobId);
}
