package com.alykoff.diff_tech.data

import java.util.*

data class CheckerSettingsResponse(
  val id: UUID,
  val urls: SortedSet<String>,
  val intervalMs: Long
)
