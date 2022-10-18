package com.alykoff.diff_tech.data

import com.alykoff.diff_tech.entity.CheckerSettingsEntity

fun CheckerSettingsEntity.toCheckerSettingsResponse(): CheckerSettingsResponse {
  return CheckerSettingsResponse(
    id = id,
    urls = urls,
    intervalMs = intervalMs
  )
}