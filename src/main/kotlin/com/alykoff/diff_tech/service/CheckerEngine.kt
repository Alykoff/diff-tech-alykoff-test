package com.alykoff.diff_tech.service

import arrow.core.Either
import com.alykoff.diff_tech.conf.props.CheckerAppProperties
import com.alykoff.diff_tech.data.CheckerSettingsRequest
import com.alykoff.diff_tech.data.UrlAndStatus
import com.alykoff.diff_tech.entity.CheckerSettingsEntity
import com.alykoff.diff_tech.entity.HealthEntity
import com.alykoff.diff_tech.entity.HealthStatus
import com.alykoff.diff_tech.exeption.CheckerError
import com.alykoff.diff_tech.exeption.CheckerValidationException
import com.alykoff.diff_tech.service.probe.AbstractProbe
import jakarta.annotation.PostConstruct
import mu.KLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

typealias UrlsByProbeNames = Map<String, List<String>>
typealias CheckerErrors = List<CheckerError>

@Service
class CheckerEngine(
  @Qualifier("singleThreadExecutor") private val singleThreadExecutor: ScheduledExecutorService,
  @Qualifier("schedulerChangerSingleThreadPool") private val schedulerChangerSingleThreadPool: Scheduler,
  private val checkerSettingsService: CheckerSettingsService,
  private val checkerHealthService: CheckerHealthService,
  private val initCheckerAppProperties: CheckerAppProperties,
  initProbes: List<AbstractProbe>,
) {
  private val scheduledFuture: AtomicReference<ScheduledFuture<*>> = AtomicReference()
  private val probes = initProbes.sortedByDescending { it.order() }
  private val probeByName: Map<String, AbstractProbe> by lazy { initProbes.associateBy { it.name } }

  companion object: KLogging() {
    private const val probeNotFound = "probeNotFound"
  }

  @PostConstruct
  protected fun setUp() {
    val initSetting = CheckerSettingsRequest(
      urls = sortedSetOf(),
      intervalMs = initCheckerAppProperties.intervalMs,
    )
    val errorsOrUrlsByProbeNames = validateAndGetUrlByProbeName(initSetting)
    when (errorsOrUrlsByProbeNames) {
      is Either.Left -> throw CheckerValidationException(errorsOrUrlsByProbeNames.value)
      else -> logger.debug { "Default config is ok" }
    }

    updateSetting(initSetting, errorsOrUrlsByProbeNames)
      .subscribe()
  }

  @Suppress(names = ["MoveVariableDeclarationIntoWhen"])
  fun updateSetting(settingsRequest: CheckerSettingsRequest): Mono<CheckerSettingsEntity> {
    val errorsOrUrlsByProbeNames = validateAndGetUrlByProbeName(settingsRequest)
    return updateSetting(settingsRequest, errorsOrUrlsByProbeNames)
  }

  private fun updateSetting(
    settingsRequest: CheckerSettingsRequest,
    errorsOrUrlsByProbeNames: Either<CheckerErrors, UrlsByProbeNames>
  ): Mono<CheckerSettingsEntity> {
    return when (errorsOrUrlsByProbeNames) {
      is Either.Left -> {
        Mono.error { CheckerValidationException(errorsOrUrlsByProbeNames.value) }
      }

      is Either.Right -> {
        refreshScheduler(CheckerSettingsEntity(
          id = UUID.randomUUID(),
          urls = settingsRequest.urls,
          intervalMs = settingsRequest.intervalMs
        ), errorsOrUrlsByProbeNames.value
        ).doOnError { e -> logger.warn(e) { "Error when refresh scheduler" } }
      }
    }
  }

  @Suppress(names = ["HttpUrlsUsage"])
  private fun validateAndGetUrlByProbeName(
    settingsRequest: CheckerSettingsRequest
  ): Either<CheckerErrors, UrlsByProbeNames> {
    val errors = mutableListOf<CheckerError>()
    if (settingsRequest.intervalMs <= initCheckerAppProperties.httpConnectTimeoutMs) {
      errors.add(CheckerError(
        "Variable `intervalMs` is wrong, because intervalMs <= httpConnectTimeoutMs"
      ))
    }
    val urlByProbeName = getUrlByProbeName(settingsRequest.urls)
    val urlsWithoutProb = urlByProbeName[probeNotFound]
    if (urlsWithoutProb?.isNotEmpty() == true) {
      errors.add(CheckerError("Some url in variable urls doesn't match with probes. " +
          "List of urls: $urlsWithoutProb"
      ))
    }
    return if (errors.isNotEmpty()) {
      Either.Left(errors)
    } else {
      Either.Right(urlByProbeName)
    }
  }

  private fun getUrlByProbeName(urls: SortedSet<String>): UrlsByProbeNames {
    return urls.fold(ConcurrentHashMap<String, MutableList<String>>()) { acc, url ->
      val probeName = probes.firstOrNull { it.isHandledUrl(url) }?.name ?: probeNotFound
      acc.compute(probeName) { _, oldUrls ->
        val newUrls = oldUrls ?: mutableListOf()
        newUrls.add(url)
        return@compute newUrls
      }
      return@fold acc
    }
  }

  @Suppress(names = ["SimpleRedundantLet"])
  private fun refreshScheduler(
    setting: CheckerSettingsEntity,
    urlsByProbeNames: UrlsByProbeNames
  ): Mono<CheckerSettingsEntity> {
    logger.debug { "Start refresh health scheduler" }
    return setting.urls.map { url -> initHealth(url, setting.id) }
      .toSet()
      .let { initHealths -> checkerHealthService.saveAll(initHealths) }
      .collectList()
      .flatMap {
        return@flatMap Mono.just(Any())
          // for prevent race condition:
          // manage changing settings refs only in one thread pool (schedulerChangerSingleThreadPool)
          .publishOn(schedulerChangerSingleThreadPool)
          .flatMap { checkerSettingsService.save(setting) }
          .map { newSetting ->
            scheduledFuture.getAndSet(
              singleThreadExecutor.scheduleAtFixedRate({
                logger.debug {
                  "Next check, urls: ${setting.urls}, interval: ${setting.intervalMs}, id: ${setting.id}"
                }

                startProbe(urlsByProbeNames)
                  .map { checkerHealthService.save(HealthEntity(
                    name = it.url,
                    state = it.status,
                    time = System.currentTimeMillis(),
                    settingId = setting.id
                  ))}.subscribe()
              }, initCheckerAppProperties.initDelayMs, setting.intervalMs, TimeUnit.MILLISECONDS)
            )?.let { oldTaskScheduler -> oldTaskScheduler.cancel(true) }
            return@map newSetting
          }.publishOn(Schedulers.parallel())
      }.doOnError { e -> logger.error(e) { "Error when init scheduler" } }
  }

  private fun startProbe(urlsByProbeNames: UrlsByProbeNames): Flux<UrlAndStatus> {
    return Flux.fromIterable(
      urlsByProbeNames.map { (probeName, urls) ->
        probeByName[probeName]
          ?.probeAsync(urls)
          ?: run {
            logger.error { "Impossible: probe with name: $probeName not found" }
            Flux.empty()
          }
      }
    ).flatMap { it }
  }

  private fun initHealth(url: String, settingId: UUID): HealthEntity {
    return HealthEntity(
      name = url,
      state = HealthStatus.UNKNOWN,
      time = System.currentTimeMillis(),
      settingId = settingId
    )
  }
}