package com.alykoff.diff_tech.http

import com.alykoff.diff_tech.entity.HealthStatus
import org.springframework.http.HttpStatus
import java.net.http.HttpResponse

fun <T> HttpResponse<T>.toHealthStatus(): HealthStatus {
  return if (this.statusCode() == HttpStatus.OK.value()) {
    HealthStatus.UP
  } else {
    HealthStatus.DOWN
  }
}