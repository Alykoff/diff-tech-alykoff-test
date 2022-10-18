package com.alykoff.diff_tech.repo

import com.alykoff.diff_tech.entity.HealthEntity
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

  fun saveAll(savedHealth: Set<HealthEntity>): Flux<HealthEntity> {
    if (savedHealth.isEmpty()) return Flux.empty()

    val settingsIds = savedHealth.map { it.settingId }.toSet()
    if (settingsIds.size != 1) {
      logger.warn { "Not the same settingsIds: $settingsIds" }
      return Flux.empty()
    }
    val settingId = settingsIds.iterator().next()
    healthByNameBySettingId.compute(settingId) { _, statusByUrl ->
      val newStatusByUrl = statusByUrl ?: ConcurrentHashMap<String, HealthEntity>()
      val copyNewStatusByUrl = HashMap(newStatusByUrl)
      newStatusByUrl.putAll(
        savedHealth.filter { (copyNewStatusByUrl[it.name]?.time ?: Long.MIN_VALUE) <= it.time }
          .associateBy { it.name }
      )
      return@compute newStatusByUrl
    }
    return savedHealth.toFlux()
  }
}