package com.alykoff.diff_tech.conf

import com.alykoff.diff_tech.conf.props.CheckerAppProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers
import java.net.http.HttpClient
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor

@Configuration
class CheckerConfiguration {
  
  @Bean(destroyMethod = "shutdown")
  @Qualifier("singleThreadExecutor")
  fun singleThreadExecutor(): ScheduledExecutorService {
    return ScheduledThreadPoolExecutor(1)
  }

  @Bean(destroyMethod = "shutdown")
  @Qualifier("httpClientExecutor")
  fun httpClientExecutor(initCheckerAppProperties: CheckerAppProperties): ExecutorService {
    return Executors.newFixedThreadPool(initCheckerAppProperties.httpClientExecutorThreads)
  }

  @Bean
  fun httpClient(
    initCheckerAppProperties: CheckerAppProperties,
    @Qualifier("httpClientExecutor") httpClientExecutor: ExecutorService
  ): HttpClient {
    return HttpClient.newBuilder()
      .connectTimeout(Duration.of(initCheckerAppProperties.httpConnectTimeoutMs, ChronoUnit.MILLIS))
      .executor(httpClientExecutor)
      .followRedirects(HttpClient.Redirect.ALWAYS)
      .build()
  }

  @Bean(destroyMethod = "dispose")
  @Qualifier("schedulerChangerSingleThreadPool")
  fun schedulerChangerSingleThreadPool(): Scheduler {
    return Schedulers.newSingle("scheduler-change-single")
  }
}