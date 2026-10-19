package io.github.rmia4.worktimer.ui.summary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rmia4.worktimer.data.Task
import io.github.rmia4.worktimer.ui.components.palette
import io.github.rmia4.worktimer.ui.timer.Tabular
import io.github.rmia4.worktimer.util.dayStart
import io.github.rmia4.worktimer.util.duration
import io.github.rmia4.worktimer.util.elapsedOf
import io.github.rmia4.worktimer.util.statusLabel
import io.github.rmia4.worktimer.util.timeLabel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// 크롬 확장 activity-clock.js(웹앱 daily-activity-clock.tsx)의 하루 활동 시계. 좌표는 240×240 기준이다.
// 마우스를 올리는 대신 구간을 누르면 작업 정보를 시계 아래 카드에 보여 주고, 같은 작업의 구간을 함께 강조한다.
private const val DAY_MS = 24 * 60 * 60 * 1000L
private const val RADIUS = 98f
private const val LANE_GAP = 15f
private const val GUIDE_RADIUS = RADIUS - LANE_GAP * 2
private const val LABEL_RADIUS = RADIUS - LANE_GAP * 3
private const val NEARBY_GAP_MS = 40 * 60 * 1000L
private const val GOLDEN_ANGLE = 137.508f
private val MARKS = List(8) { i -> i * 3 to (i / 8.0 * PI * 2 - PI / 2) }

data class Segment(val task: Task, val color: Color, val start: Long, val end: Long, val startRatio: Float, val durationRatio: Float, val lane: Int)

private fun firstStart(t: Task) = t.sessions?.firstOrNull()?.startAt ?: t.startedAt ?: 0

// FNV-1a 해시로 작업마다 같은 색을 고른다(확장과 같은 값).
private fun idHue(id: String): Int {
    var hash = 2166136261u.toInt()
    for (ch in id) hash = (hash xor ch.code) * 16777619
    return (hash.toUInt() % 360u).toInt()
}

private fun taskColors(tasks: List<Task>): Map<String, Color> {
    val minGap = min(30f, 360f / max(1, tasks.size) / 1.5f)
    val used = mutableListOf<Float>()
    val colors = mutableMapOf<String, Color>()
    for (task in tasks.sortedWith(compareBy<Task> { firstStart(it) }.thenBy { it.id })) {
        var hue = idHue(task.id).toFloat()
        for (attempt in 0 until 24) {
            if (used.all { o -> min(abs(hue - o), 360 - abs(hue - o)) >= minGap }) break
            hue = (hue + GOLDEN_ANGLE) % 360
        }
        used += hue
        colors[task.id] = Color.hsl(hue.roundToInt().toFloat(), 0.62f, 0.76f)
    }
    return colors
}

// 하루(한국 시간) 안으로 자른 측정 구간. 가까운 구간은 안쪽 줄로 옮겨 겹치지 않게 한다.
fun activitySegments(tasks: List<Task>, day: String, now: Long): List<Segment> {
    val start0 = dayStart(day)
    val end0 = start0 + DAY_MS
    val colors = taskColors(tasks)
    val segments = tasks.flatMap { task ->
        val spans = task.sessions?.takeIf { it.isNotEmpty() }?.map { it.startAt to it.endAt }
            ?: task.startedAt?.let { listOf(it to (task.endedAt ?: if (task.done) it else null)) }
            ?: emptyList()
        spans.mapNotNull { (s, e) ->
            val start = max(start0, s)
            val end = min(end0, e ?: now)
            if (end <= start) null
            else Segment(task, colors.getValue(task.id), start, end, (start - start0).toFloat() / DAY_MS, (end - start).toFloat() / DAY_MS, 0)
        }
    }.sortedWith(compareBy<Segment> { it.start }.thenBy { it.end })
    var previous: Segment? = null
    return segments.map { s ->
        val lane = if (previous != null && s.start - previous!!.end <= NEARBY_GAP_MS && previous!!.lane == 0) 1 else 0
        s.copy(lane = lane).also { previous = it }
    }
}

private fun laneRadius(s: Segment) = max(44f, RADIUS - s.lane * LANE_GAP)

// 누른 점(시계 좌표 0~240)에 있는 구간. 중심에서의 거리로 줄을 고르고 각도로 구간을 고른다.
private fun segmentAt(segments: List<Segment>, x: Float, y: Float): Segment? {
    val dx = x - 120
    val dy = y - 120
    val dist = hypot(dx, dy)
    val ratio = (((atan2(dy, dx) + PI / 2) / (PI * 2) + 1) % 1).toFloat()
    var best: Segment? = null
    var bestGap = Float.MAX_VALUE
    for (s in segments) {
        val r = laneRadius(s)
        if (abs(dist - r) > LANE_GAP / 2) continue
        val slop = 10 / (PI.toFloat() * 2 * r) // 둥근 끝과 손가락 크기만큼 조금 넓게 잡는다
        val gap = maxOf(0f, s.startRatio - ratio, ratio - s.startRatio - s.durationRatio)
        if (gap <= slop && gap < bestGap) {
            best = s
            bestGap = gap
        }
    }
    return best
}

// 고른 구간은 부모가 들고 있다(화면의 빈 곳을 누르거나 다른 탭으로 가면 부모가 지운다).
@Composable
fun ActivityClock(segments: List<Segment>, now: Long, picked: Segment?, onPick: (Segment?) -> Unit) {
    val c = palette()
    val measurer = rememberTextMeasurer()
    val angle = ((now + 9 * 3600_000L) % DAY_MS).toDouble() / DAY_MS * PI * 2 - PI / 2
    val tip = picked?.let { p -> segments.firstOrNull { it.task.id == p.task.id && it.start == p.start } }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .padding(top = 8.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .semantics { contentDescription = "하루 활동 시계" }
                .pointerInput(segments) {
                    detectTapGestures { o -> onPick(segmentAt(segments, o.x * 240 / size.width, o.y * 240 / size.height)) }
                },
        ) {
            scale(size.width / 240f, Offset.Zero) {
                val center = Offset(120f, 120f)
                drawCircle(c.bg, RADIUS, center)
                drawCircle(c.line, RADIUS, center, style = Stroke(12f))
                for (s in segments) {
                    val r = laneRadius(s)
                    drawArc(
                        s.color,
                        startAngle = -90f + s.startRatio * 360,
                        sweepAngle = max(s.durationRatio * 360, 0.01f),
                        useCenter = false,
                        topLeft = Offset(120 - r, 120 - r),
                        size = Size(r * 2, r * 2),
                        style = Stroke(if (tip?.task?.id == s.task.id) 16f else 12f, cap = StrokeCap.Round),
                    )
                }
                drawCircle(c.line, GUIDE_RADIUS, center, style = Stroke(1f))
                for ((_, a) in MARKS) drawCircle(c.muted, 3f, Offset(120 + cos(a).toFloat() * GUIDE_RADIUS, 120 + sin(a).toFloat() * GUIDE_RADIUS))
                drawLine(c.muted, center, Offset(120 + cos(angle).toFloat() * RADIUS, 120 + sin(angle).toFloat() * RADIUS), 2f, StrokeCap.Round)
                drawCircle(c.bg, 7f, center)
                drawCircle(c.muted, 5f, center)
                for ((hour, a) in MARKS) {
                    val layout = measurer.measure(hour.toString(), TextStyle(color = c.muted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold), density = androidx.compose.ui.unit.Density(1f))
                    drawText(layout, topLeft = Offset(120 + cos(a).toFloat() * LABEL_RADIUS - layout.size.width / 2f, 120 + sin(a).toFloat() * LABEL_RADIUS - layout.size.height / 2f))
                }
            }
        }
        // 고르지 않았을 때도 자리를 비워 두어 구간을 고르거나 닫아도 화면이 움직이지 않는다.
        Column(
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(108.dp)
                .let { if (tip != null) it.background(c.bg, RoundedCornerShape(8.dp)).border(1.dp, c.line, RoundedCornerShape(8.dp)) else it }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            if (tip != null) TaskInfo(tip, segments.filter { it.task.id == tip.task.id }, now)
        }
    }
}

// 고른 구간의 작업 정보. 제목은 한 줄로 자른다.
@Composable
private fun TaskInfo(tip: Segment, segments: List<Segment>, now: Long) {
    val c = palette()
    val t = tip.task
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).background(tip.color, CircleShape))
        Text(t.title, Modifier.weight(1f), color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(statusLabel(t.status), color = c.muted, fontSize = 12.sp)
    }
    for ((label, value) in listOf(
        "이 구간" to "${timeLabel(tip.start)}–${timeLabel(tip.end)} (${duration(tip.end - tip.start)})",
        "이 날 측정" to "${duration(segments.sumOf { it.end - it.start })} · 구간 ${segments.size}개",
        "총 소요" to duration(elapsedOf(t, now)),
    )) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, Modifier.width(64.dp), color = c.muted, fontSize = 13.sp)
            Text(value, color = c.fg, fontSize = 13.sp, style = Tabular)
        }
    }
}
