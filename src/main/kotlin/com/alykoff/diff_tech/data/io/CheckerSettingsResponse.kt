package com.alykoff.diff_tech.data.io

import java.util.*

data class CheckerSettingsResponse(
  val id: UUID,
  val urls: SortedSet<String>,
  val intervalMs: Long
)
