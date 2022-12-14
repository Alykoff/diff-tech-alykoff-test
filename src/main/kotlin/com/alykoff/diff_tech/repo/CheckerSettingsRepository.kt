package com.alykoff.diff_tech.repo

import com.alykoff.diff_tech.entity.CheckerSettingsEntity
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicReference

@Suppress(names = ["SpringJavaInjectionPointsAutowiringInspection"])
@Service
class CheckerSettingsRepository {
  private val ref: AtomicReference<CheckerSettingsEntity> = AtomicReference<CheckerSettingsEntity>()

  fun getLast(): Mono<CheckerSettingsEntity> {
    return ref.get()?.let { Mono.just(it) } ?: Mono.empty()
  }

  fun save(newEntity: CheckerSettingsEntity): Mono<CheckerSettingsEntity> {
    ref.set(newEntity)
    return Mono.just(newEntity)
  }
}