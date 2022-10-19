package com.alykoff.diff_tech.entity

import java.util.UUID

data class HealthEntity(
  val name: String,
  val state: HealthStatus,
  val time: Long,
  val settingId: UUID
)