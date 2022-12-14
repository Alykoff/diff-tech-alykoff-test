package com.alykoff.diff_tech.service

import com.alykoff.diff_tech.entity.HealthEntity
import com.alykoff.diff_tech.repo.HealthRepository
import mu.KLogging
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.kotlin.core.publisher.toFlux
import java.util.*

@Service
class CheckerHealthService(
  private val checkerSettingsService: CheckerSettingsService,
  private val healthRepository: HealthRepository
) {
  companion object: KLogging()

  fun findAll(): Flux<HealthEntity> {
    return checkerSettingsService.getActualSetting()
      .toFlux()
      .flatMap { setting -> healthRepository.findAllBySettingId(setting.id) }
  }

  fun saveAll(entities: Set<HealthEntity>, copyLastHealthFromConfId: UUID?): Flux<HealthEntity> {
    return healthRepository.saveAll(entities, copyLastHealthFromConfId)
  }

  fun save(entities: HealthEntity): Flux<HealthEntity> {
    return healthRepository.saveAll(setOf(entities), null)
  }

  fun removeBySettingId(settingId: UUID): Flux<HealthEntity> {
    return healthRepository.removeBySettingId(settingId)
  }
}
