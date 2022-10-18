package com.alykoff.diff_tech.exeption

import com.alykoff.diff_tech.data.CheckerError

@Suppress(names = ["MemberVisibilityCanBePrivate", "CanBeParameter"])
class CheckerValidationException(val errors: List<CheckerError>): RuntimeException(
  errors.joinToString(separator = ",")
)