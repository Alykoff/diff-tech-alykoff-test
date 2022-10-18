package com.alykoff.diff_tech.repo

import com.alykoff.diff_tech.conf.props.CheckerAppProperties
import com.alykoff.diff_tech.entity.CheckerSettingsEntity
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.*
import java.util.concurrent.atomic.AtomicReference

@Suppress(names = ["SpringJavaInjectionPointsAutowiringInspection"])
@Service
class CheckerSettingsRepository {
  private val ref: AtomicReference<CheckerSettingsEntity> = AtomicReference<CheckerSettingsEntity>()

  fun getLast(): Mono<CheckerSettingsEntity> {
    return Mono.just(ref.get())
  }

  fun save(newEntity: CheckerSettingsEntity): Mono<CheckerSettingsEntity> {
    ref.set(newEntity)
    return Mono.just(newEntity)
  }
}