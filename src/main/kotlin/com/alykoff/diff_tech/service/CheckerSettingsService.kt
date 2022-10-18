package com.alykoff.diff_tech.service

import com.alykoff.diff_tech.entity.CheckerSettingsEntity
import com.alykoff.diff_tech.repo.CheckerSettingsRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class CheckerSettingsService(
  private val checkerSettingsRepository: CheckerSettingsRepository
) {
  fun getActualSetting(): Mono<CheckerSettingsEntity> {
    return checkerSettingsRepository.getLast()
  }

  fun save(newSetting: CheckerSettingsEntity): Mono<CheckerSettingsEntity> {
    return checkerSettingsRepository.save(newSetting)
  }
}