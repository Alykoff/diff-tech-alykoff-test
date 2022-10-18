package com.alykoff.diff_tech.controller

import com.alykoff.diff_tech.data.CheckerSettingsRequest
import com.alykoff.diff_tech.data.CheckerSettingsResponse
import com.alykoff.diff_tech.data.toCheckerSettingsResponse
import com.alykoff.diff_tech.service.CheckerEngine
import com.alykoff.diff_tech.service.CheckerSettingsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
class CheckerSettingsController(
  private val checkerSettingsService: CheckerSettingsService,
  private val checkerEngine: CheckerEngine
) {
  @GetMapping("/setting")
  fun getHealthSetting(): Mono<CheckerSettingsResponse> {
    return checkerSettingsService.getActualSetting().map { it.toCheckerSettingsResponse() }
  }

  @PostMapping("/setting")
  fun updateHealthSetting(@RequestBody settingChecker: CheckerSettingsRequest): Mono<CheckerSettingsResponse> {
    return checkerEngine.updateSetting(settingChecker).map { it.toCheckerSettingsResponse() }
  }
}