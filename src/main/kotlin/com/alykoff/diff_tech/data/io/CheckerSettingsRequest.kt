package com.alykoff.diff_tech.data.io

import java.util.SortedSet

data class CheckerSettingsRequest(
  val urls: SortedSet<String>,
  val intervalMs: Long
)
