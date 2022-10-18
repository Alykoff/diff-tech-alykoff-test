package com.alykoff.diff_tech.exeption

@Suppress(names = ["MemberVisibilityCanBePrivate", "CanBeParameter"])
class CheckerValidationException(val errors: List<CheckerError>): RuntimeException(
  errors.joinToString(separator = ",")
)