package io.github.rmia4.worktimer.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 시각·시간은 모두 밀리초. started는 측정 중일 때 이번 구간의 시작 시각이다.
@Serializable
data class Task(
    val id: String,
    val day: String,
    val title: String,
    val note: String = "",
    val result: String? = null,
    val status: String,
    val elapsed: Long,
    val started: Long? = null,
    val target: Long = 0,
    @SerialName("started_at") val startedAt: Long? = null,
    @SerialName("ended_at") val endedAt: Long? = null,
    val version: Int,
    val sessions: List<Session>? = null,
) {
    val done get() = status == "done"
}

@Serializable
data class Session(
    @SerialName("start_at") val startAt: Long,
    @SerialName("end_at") val endAt: Long? = null,
)

@Serializable
data class Memo(
    val id: String,
    val title: String = "",
    val body: String = "",
    val collapsed: Boolean? = null,
    val version: Int,
)

@Serializable
data class CalendarDay(
    val day: String,
    val count: Int = 0,
    val total: Long = 0,
    val running: Int = 0,
)

@Serializable
data class Connection(
    val id: String,
    val name: String,
    val host: String? = null,
    val scopes: List<String> = emptyList(),
    val created: Long,
    val lastUsed: Long? = null,
)
