package ru.heatrk.antivirus.utils

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

fun timestampToLocalDateTime(timestamp: Long?): LocalDateTime {
    val instant = timestamp?.let { Instant.ofEpochMilli(it) } ?: Instant.now()
    val zone = ZoneId.systemDefault()
    return LocalDateTime.ofInstant(instant, zone)
}

fun LocalDateTime.toTimestamp(): Long =
    atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()