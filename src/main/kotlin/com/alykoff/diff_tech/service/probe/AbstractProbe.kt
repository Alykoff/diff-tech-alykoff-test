package com.alykoff.diff_tech.service.probe

import com.alykoff.diff_tech.data.UrlAndStatus
import reactor.core.publisher.Flux

abstract class AbstractProbe {
  abstract val name: String
  abstract val schemas: Set<String>

  val checkerSchemaRegex: Regex by lazy {
    "^(${schemas.joinToString(separator = "|")})://.*$".toRegex()
  }

  abstract fun isHandledUrl(url: String): Boolean
  abstract fun probeAsync(urls: List<String>): Flux<UrlAndStatus>

  open fun order() = Long.MIN_VALUE
}