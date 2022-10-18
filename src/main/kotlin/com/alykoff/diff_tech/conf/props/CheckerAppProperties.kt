package com.alykoff.diff_tech.conf.props

import org.springframework.boot.context.properties.ConfigurationProperties
import java.util.SortedSet

@Suppress(names = ["SpringJavaInjectionPointsAutowiringInspection"])
@ConfigurationProperties(prefix = "checker")
data class CheckerAppProperties(
  val urls: SortedSet<String>,
  val intervalMs: Long,
  val initDelayMs: Long,
  val httpConnectTimeoutMs: Long,
  val httpTimeoutMs: Long,
  val httpClientExecutorThreads: Int
)