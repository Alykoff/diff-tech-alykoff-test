package com.alykoff.diff_tech.entity

import java.util.UUID

@Suppress(names = ["unused"])
class HealthEntity(
  val name: String,
  val state: HealthStatus,
  val time: Long,
  val settingId: UUID
)