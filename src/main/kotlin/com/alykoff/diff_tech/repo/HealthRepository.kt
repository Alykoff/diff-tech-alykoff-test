package com.alykoff.diff_tech.repo

import com.alykoff.diff_tech.entity.HealthEntity
import com.alykoff.diff_tech.entity.HealthStatus
import mu.KLogging
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.kotlin.core.publisher.toFlux
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class HealthRepository {
  companion object: KLogging()

  private val healthByNameBySettingId: ConcurrentHashMap<UUID, ConcurrentHashMap<String, HealthEntity>> = ConcurrentHashMap()

  fun findAllBySettingId(settingId: UUID): Flux<HealthEntity> {
    return healthByNameBySettingId.getOrDefault(settingId, mapOf())
      .values
      .sortedBy { it.name }
      .toFlux()
  }

  fun saveAll(savedHealth: Set<HealthEntity>, oldSettingId: UUID?): Flux<HealthEntity> {
    if (savedHealth.isEmpty()) return Flux.empty()

    val settingsIds = savedHealth.map { it.settingId }.toSet()
    if (settingsIds.size != 1) {
      logger.warn { "Not the same settingsIds: $settingsIds" }
      return Flux.empty()
    }
    val settingId = settingsIds.iterator().next()
    val prevStatusByName = oldSettingId
      ?.let { healthByNameBySettingId[it] }
      ?.let { ConcurrentHashMap(it) }
      ?.filterValues { it.state != HealthStatus.UNKNOWN }
      ?: ConcurrentHashMap()
    healthByNameBySettingId.compute(settingId) { _, statusByUrl ->
      val newStatusByUrl = statusByUrl ?: ConcurrentHashMap<String, HealthEntity>()
      savedHealth.map { health -> fillHealthFromOldSetting(health, prevStatusByName) }
        .filter { health -> (newStatusByUrl[health.name]?.time ?: Long.MIN_VALUE) <= health.time }
        .associateBy { health -> health.name }
        .let { newStatusByUrl.putAll(it) }
      return@compute newStatusByUrl
    }
    return savedHealth.toFlux()
  }

  fun removeBySettingId(settingId: UUID): Flux<HealthEntity> {
    return healthByNameBySettingId.remove(settingId)?.let {
      Flux.fromIterable(it.values)
    } ?: Flux.empty()
  }

  private fun fillHealthFromOldSetting(
    health: HealthEntity,
    prevStatusByName: Map<String, HealthEntity>
  ): HealthEntity {
    val healthsFromOldSetting = prevStatusByName[health.name]
    // this behaviour maybe the different ones;
    // use the simplest merge strategy.
    val shouldGetOldState = healthsFromOldSetting?.let { oldHealth ->
      (health.state == HealthStatus.UNKNOWN) || (oldHealth.time > health.time)
    } ?: false
    return if (shouldGetOldState) {
      health.copy(state = healthsFromOldSetting?.state ?: health.state)
    } else {
      health
    }
  }
}