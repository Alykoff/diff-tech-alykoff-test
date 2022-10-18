package com.alykoff.diff_tech.entity

import java.util.*

data class CheckerSettingsEntity(
  val id: UUID,
  val urls: SortedSet<String>,
  val intervalMs: Long
)
