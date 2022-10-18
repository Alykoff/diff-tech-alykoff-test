package com.alykoff.diff_tech

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties
@ConfigurationPropertiesScan("com.alykoff.diff_tech.conf.props")
class DiffTechTestAlykoff2Application

fun main(args: Array<String>) {
  runApplication<DiffTechTestAlykoff2Application>(*args)
}
