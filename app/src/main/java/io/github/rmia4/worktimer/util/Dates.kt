package io.github.rmia4.worktimer.util

import io.github.rmia4.worktimer.data.Task
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

// 크롬 확장 dates.js의 날짜·시간 함수. 날짜는 모두 한국 시간(UTC+9) 기준 "YYYY-MM-DD" 문자열이다.
val KST: ZoneOffset = ZoneOffset.ofHours(9)

fun today(): String = LocalDate.now(KST).toString()

private fun pad(n: Long) = n.toString().padStart(2, '0')

fun duration(ms: Long): String {
    val s = maxOf(0, ms / 1000)
    return "${pad(s / 3600)}:${pad(s / 60 % 60)}:${pad(s % 60)}"
}

fun statusLabel(status: String) = when (status) {
    "done" -> "완료"
    "running" -> "측정 중"
    else -> "일시정지"
}

private fun kst(t: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(t), KST)

fun timeLabel(t: Long?): String = if (t == null) "미기록" else kst(t).let { "${pad(it.hour.toLong())}:${pad(it.minute.toLong())}" }

fun timestampLabel(t: Long?): String {
    if (t == null) return "미기록"
    val d = kst(t)
    return "${pad(d.monthValue.toLong())}. ${pad(d.dayOfMonth.toLong())}. ${pad(d.hour.toLong())}:${pad(d.minute.toLong())}:${pad(d.second.toLong())}"
}

fun shiftDay(value: String, offset: Long): String = LocalDate.parse(value).plusDays(offset).toString()

fun weekday(value: String): String = "일월화수목금토"[LocalDate.parse(value).dayOfWeek.value % 7].toString()

// 하루(한국 시간)가 시작하는 시각.
fun dayStart(value: String): Long = LocalDate.parse(value).atStartOfDay().toInstant(KST).toEpochMilli()

fun elapsedOf(t: Task, now: Long): Long = t.elapsed + (t.started?.let { maxOf(0, now - it) } ?: 0)
