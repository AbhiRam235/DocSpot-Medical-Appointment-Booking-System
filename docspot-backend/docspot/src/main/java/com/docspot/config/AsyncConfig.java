package com.docspot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Enables @Async and provides the default thread pool used by all @Async methods.
 *
 * Implementing AsyncConfigurer means every @Async annotation (with no explicit
 * qualifier) uses this pool — EmailService sends, NotificationService listeners,
 * all handled here without blocking HTTP response threads.
 *
 * Pool sizing (resume-level defaults):
 *   corePoolSize  = 2  — always alive, ready to pick up tasks immediately
 *   maxPoolSize   = 10 — spun up when queue fills; capped to avoid overwhelming SMTP
 *   queueCapacity = 100 — tasks queue here before new threads are created
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("docspot-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("Async executor initialised — pool: {}/{}, queue: {}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(),
                executor.getQueueCapacity());
        return executor;
    }

    /**
     * Logs unhandled exceptions thrown inside @Async methods.
     * Without this, exceptions in async methods are silently swallowed.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                log.error("Uncaught exception in async method [{}]: {}", method.getName(), ex.getMessage(), ex);
    }
}
