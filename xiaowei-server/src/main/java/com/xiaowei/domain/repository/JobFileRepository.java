package com.xiaowei.domain.repository;

import com.xiaowei.domain.entity.JobFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobFileRepository extends JpaRepository<JobFile, Long> {

    List<JobFile> findByJobIdOrderByCreatedAtDesc(Long jobId);

    boolean existsByJobId(Long jobId);
}
