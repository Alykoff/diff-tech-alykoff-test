package com.alykoff.diff_tech.data

import java.util.SortedSet

data class CheckerSettingsRequest(
  val urls: SortedSet<String>,
  val intervalMs: Long
)
