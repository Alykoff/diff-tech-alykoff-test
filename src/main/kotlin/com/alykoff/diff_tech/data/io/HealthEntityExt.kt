package com.alykoff.diff_tech.data.io

import com.alykoff.diff_tech.entity.HealthEntity

fun HealthEntity.toCheckerHealthResponse(): CheckerHealthResponse {
  return CheckerHealthResponse(
    url = this.name,
    state = this.state,
    time = this.time
  )
}