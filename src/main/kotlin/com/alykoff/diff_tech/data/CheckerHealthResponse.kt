package com.alykoff.diff_tech.data

import com.alykoff.diff_tech.entity.HealthStatus

data class CheckerHealthResponse(
  val url: String,
  val state: HealthStatus,
  val time: Long
)
