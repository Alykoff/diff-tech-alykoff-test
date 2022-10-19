package com.alykoff.diff_tech.service

import arrow.core.Either
import com.alykoff.diff_tech.conf.props.CheckerAppProperties
import com.alykoff.diff_tech.data.io.CheckerSettingsRequest
import com.alykoff.diff_tech.data.UrlAndStatus
import com.alykoff.diff_tech.entity.CheckerSettingsEntity
import com.alykoff.diff_tech.entity.HealthEntity
import com.alykoff.diff_tech.entity.HealthStatus
import com.alykoff.diff_tech.data.CheckerError
import com.alykoff.diff_tech.exeption.CheckerLogicException
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
import java.lang.Long.max
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function
import kotlin.jvm.optionals.getOrNull

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
    if (settingsRequest.intervalMs <= 0) {
      errors.add(CheckerError("Setting parameter intervalMs should be > 0"))
    }
    // look up the maximum boundary for interval
    val maxTimeout = max(initCheckerAppProperties.httpConnectTimeoutMs, initCheckerAppProperties.httpTimeoutMs)
    if (settingsRequest.intervalMs <= maxTimeout) {
      errors.add(CheckerError("Variable `intervalMs` is wrong, because intervalMs <= httpConnectTimeoutMs"))
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

  @OptIn(ExperimentalStdlibApi::class)
  @Suppress(names = ["BlockingMethodInNonBlockingContext"])
  private fun refreshScheduler(
    newSetting: CheckerSettingsEntity,
    urlsByProbeNames: UrlsByProbeNames
  ): Mono<CheckerSettingsEntity> {
    logger.debug { "Start refresh health scheduler" }
    val newHealths = newSetting.urls.map { url -> initHealth(url, newSetting.id) }.toSet()
    return checkerSettingsService.getActualSetting()
      .map { Optional.of(it) }
      // when we just started we don't have any setting, so we should handle default branch
      .defaultIfEmpty(Optional.empty<CheckerSettingsEntity>())
      // we save the first health entities before create setting entity,
      // and if some exception apparent here we stay keep these entities,
      // to prevent this in prod env we may create special scheduling task
      // for removing handling entities
      .flatMap { prevSetting ->
        checkerHealthService.saveAll(newHealths, prevSetting.getOrNull()?.id)
          .collectList()
          .map { prevSetting.map { it.id } }
      }
      // for prevent race condition:
      // manage changing settings refs only in one thread pool scheduler (schedulerChangerSingleThreadPool)
      .publishOn(schedulerChangerSingleThreadPool)
      .map { prevSettingId ->
        val savedSetting = checkerSettingsService.save(newSetting)
          // . we use block() method at this place, it's ok because we use the special thread pool,
          //     and we don't expect that we'll have a lot of setting changes;
          // . but if we have a lot of replicas we should also add read/write replica roles in our app;
          // . for the case we are setting timeout, but in prod environment it should be setting at a db side.
          .block(Duration.of(1L, ChronoUnit.MINUTES))
          ?: throw CheckerLogicException("Setting didn't save, didn't find entity after calling save method")

        recreateProbeTasks(savedSetting, urlsByProbeNames)

        // remove old health data by async
        prevSettingId.getOrNull()?.run {
          checkerHealthService.removeBySettingId(this)
            .subscribe()
        }
        return@map savedSetting
      }
      // return manage to default scheduler
      .publishOn(Schedulers.parallel())
      .doOnError { e -> logger.error(e) { "Error when init scheduler" } }
  }

  @Suppress(names = ["SimpleRedundantLet"])
  private fun recreateProbeTasks(
    setting: CheckerSettingsEntity,
    urlsByProbeNames: UrlsByProbeNames
  ) {
    scheduledFuture.getAndSet(
      singleThreadExecutor.scheduleAtFixedRate({
        logger.debug { "Next check, urls: ${setting.urls}, interval: ${setting.intervalMs}, id: ${setting.id}" }

        startProbe(urlsByProbeNames)
          .map { checkerHealthService.save(HealthEntity(
            name = it.url,
            state = it.status,
            time = System.currentTimeMillis(),
            settingId = setting.id
          ))}.subscribe()
      }, initCheckerAppProperties.initDelayMs, setting.intervalMs, TimeUnit.MILLISECONDS)
    )?.let { oldTaskScheduler -> oldTaskScheduler.cancel(true) }
  }

  private fun startProbe(urlsByProbeNames: UrlsByProbeNames): Flux<UrlAndStatus> {
    return urlsByProbeNames.map { (probeName, urls) ->
        probeByName[probeName]
          ?.probeAsync(urls)
          ?: run {
            logger.error { "Impossible: probe with name: $probeName not found" }
            Flux.empty()
          }
      }.let { Flux.fromIterable(it) }
      .flatMap(Function.identity())
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