package com.xiaowei.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

  @Value("${xiaowei.job.core-pool-size:2}")
  private int jobCorePoolSize;

  @Value("${xiaowei.job.max-pool-size:8}")
  private int jobMaxPoolSize;

  @Value("${xiaowei.job.queue-capacity:200}")
  private int jobQueueCapacity;

  @Bean(name = "jobTaskExecutor")
  public TaskExecutor jobTaskExecutor() {
    ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
    ex.setCorePoolSize(Math.max(1, jobCorePoolSize));
    ex.setMaxPoolSize(Math.max(jobCorePoolSize, jobMaxPoolSize));
    ex.setQueueCapacity(Math.max(50, jobQueueCapacity));
    ex.setThreadNamePrefix("job-async-");
    ex.setWaitForTasksToCompleteOnShutdown(true);
    ex.setAwaitTerminationSeconds(60);
    ex.initialize();
    return ex;
  }
}
