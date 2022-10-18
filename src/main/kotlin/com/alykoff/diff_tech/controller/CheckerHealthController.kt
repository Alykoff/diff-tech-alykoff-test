package com.alykoff.diff_tech.controller

import com.alykoff.diff_tech.data.CheckerHealthResponse
import com.alykoff.diff_tech.data.toCheckerHealthResponse
import com.alykoff.diff_tech.entity.HealthEntity
import com.alykoff.diff_tech.service.CheckerHealthService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
class CheckerHealthController(
  private val checkerHealthService: CheckerHealthService
) {
  @GetMapping("/health")
  // Flux doesn't work here, some bug with empty json
  fun getHealths(): Mono<List<CheckerHealthResponse>> {
    return checkerHealthService.findAll()
      .map { it.toCheckerHealthResponse() }
      .collectList()
  }
}