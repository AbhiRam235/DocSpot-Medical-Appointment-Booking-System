package com.docspot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Enables Spring Retry's @Retryable/@Backoff annotations application-wide.
 * Used by EmailService — transient SMTP failures (connection blips, brief
 * mail-server unavailability) get retried automatically instead of being
 * treated as a permanent failure on the first attempt.
 *
 * Requires spring-retry + spring-boot-starter-aop on the classpath (see
 * pom.xml) — @Retryable is implemented as a Spring AOP proxy advice, same
 * mechanism as @Transactional and @Async.
 */
@Configuration
@EnableRetry
public class RetryConfig {
}
