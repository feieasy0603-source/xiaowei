package com.xiaowei.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 在独立线程池执行生成任务（避免 @Async 在 afterCommit 等场景下不触发导致任务永久 pending）。
 */
@Slf4j
@Component
public class JobAsyncRunner {

  private final JobService jobService;
  private final TaskExecutor jobTaskExecutor;

  public JobAsyncRunner(@Lazy JobService jobService, @Qualifier("jobTaskExecutor") TaskExecutor jobTaskExecutor) {
    this.jobService = jobService;
    this.jobTaskExecutor = jobTaskExecutor;
  }

  public void run(Long jobId) {
    jobTaskExecutor.execute(() -> {
      try {
        log.info("开始执行生成任务 jobId={}", jobId);
        jobService.executeJob(jobId);
      } catch (Exception e) {
        log.error("生成任务执行异常 jobId={}", jobId, e);
      }
    });
  }
}
