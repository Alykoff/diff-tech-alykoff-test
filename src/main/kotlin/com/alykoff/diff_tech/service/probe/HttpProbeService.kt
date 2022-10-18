package com.alykoff.diff_tech.service.probe

import arrow.core.sort
import com.alykoff.diff_tech.data.UrlAndStatus
import com.alykoff.diff_tech.entity.HealthStatus
import com.alykoff.diff_tech.http.toHealthStatus
import com.alykoff.diff_tech.service.CheckerEngine
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.SortedSet

@Service
class HttpProbeService(private val httpClient: HttpClient): AbstractProbe() {
  override val name: String
    get() ="HttpProbe"

  override val schemas: SortedSet<String>
    get() = sortedSetOf("http", "https")

  override fun order() = 1L

  override fun isHandledUrl(url: String): Boolean {
    return url.matches(checkerSchemaRegex)
  }

  override fun probeAsync(urls: List<String>): Flux<UrlAndStatus> {
    return urls.map { url ->
      return@map try {
        HttpRequest.newBuilder().uri(URI.create(url)).build()
          .let { request -> httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()) }
          .toMono()
          .map { response -> UrlAndStatus(url, response.toHealthStatus()) }
          .doOnError { e -> CheckerEngine.logger.debug(e) { "Error when call url: $url" } }
          .onErrorReturn(UrlAndStatus(url, HealthStatus.DOWN))
      } catch (e: Exception) {
        CheckerEngine.logger.debug(e) { "Problem when call url: $url" }
        Mono.just(UrlAndStatus(url, HealthStatus.DOWN))
      }
    }.let { Flux.concat(it) }
  }
}