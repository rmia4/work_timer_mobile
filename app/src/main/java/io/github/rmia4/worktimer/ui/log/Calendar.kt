package io.github.rmia4.worktimer.ui.log

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rmia4.worktimer.data.CalendarDay
import io.github.rmia4.worktimer.data.errorText
import io.github.rmia4.worktimer.data.json
import io.github.rmia4.worktimer.ui.components.ErrorText
import io.github.rmia4.worktimer.ui.components.Hint
import io.github.rmia4.worktimer.ui.components.Variant
import io.github.rmia4.worktimer.ui.components.WtButton
import io.github.rmia4.worktimer.ui.components.palette
import io.github.rmia4.worktimer.ui.main.MainViewModel
import io.github.rmia4.worktimer.ui.main.Month
import io.github.rmia4.worktimer.util.duration
import io.github.rmia4.worktimer.util.today
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.time.YearMonth

private fun summaryDuration(ms: Long) = duration(ms).substringBeforeLast(':')

// 크롬 확장 calendar.js(웹앱 work-calendar.tsx)의 작업 캘린더. 달마다 날짜별 작업 수·시간을 불러와 캐시한다.
// 웹앱처럼 날짜를 한 번 누르면 고르고, 고른 날짜를 다시 누르면 onChoose를 부른다.
@Composable
fun Calendar(vm: MainViewModel, day: String, onChoose: (String) -> Unit) {
    val c = palette()
    var selection by rememberSaveable { mutableStateOf(day) }
    var month by rememberSaveable { mutableStateOf(day.take(7)) }
    var error by rememberSaveable { mutableStateOf("") }
    val cached = vm.months[month]

    LaunchedEffect(month, error, cached == null) {
        if (cached != null || error.isNotEmpty()) return@LaunchedEffect
        try {
            val data = vm.api.get("/api/calendar?month=$month") ?: return@LaunchedEffect
            val days = json.decodeFromJsonElement<List<CalendarDay>>(data["days"]!!)
            vm.months[month] = Month(days.associateBy { it.day }, data["now"]!!.jsonPrimitive.long)
        } catch (e: Exception) {
            error = errorText(e, "캘린더를 불러오지 못했습니다.")
        }
    }

    fun select(value: String) {
        if (value == selection) onChoose(value) else selection = value
    }
    fun move(n: Long) {
        month = YearMonth.parse(month).plusMonths(n).toString()
        error = ""
    }
    // 측정 중인 작업 시간은 불러온 시각 이후만큼 더한다.
    fun total(d: CalendarDay?) = (d?.total ?: 0) + (d?.running ?: 0) * maxOf(0, System.currentTimeMillis() - (cached?.now ?: System.currentTimeMillis()))

    val todayKey = today()
    val ym = YearMonth.parse(month)
    val lead = ym.atDay(1).dayOfWeek.value % 7
    val cells = List(lead) { null } + (1..ym.lengthOfMonth()).map { "$month-${it.toString().padStart(2, '0')}" }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("작업 캘린더", color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            WtButton("오늘", {
                month = todayKey.take(7)
                error = ""
                select(todayKey)
            }, variant = Variant.Outline, small = true)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            WtButton("‹", { move(-1) }, variant = Variant.Outline, small = true)
            Text("${ym.year}년 ${ym.monthValue}월", color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            WtButton("›", { move(1) }, variant = Variant.Outline, small = true)
        }
        Row {
            for (w in "일월화수목금토") Text(w.toString(), Modifier.weight(1f), color = c.muted, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        for (week in cells.chunked(7)) {
            Row {
                for (i in 0 until 7) {
                    val key = week.getOrNull(i)
                    Box(Modifier.weight(1f).heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
                        if (key != null) DayCell(key, i, key == selection, key == todayKey, cached?.days?.get(key)) { select(key) }
                    }
                }
            }
        }
        if (error.isNotEmpty()) ErrorText(error)
        else Hint(
            when {
                cached != null && selection.take(7) == month -> cached.days[selection].let { "작업 ${it?.count ?: 0}개, ${summaryDuration(total(it))}" }
                cached != null -> "날짜를 선택해 주세요."
                else -> "기록을 불러오는 중입니다."
            },
        )
    }
}

@Composable
private fun DayCell(key: String, weekday: Int, selected: Boolean, isToday: Boolean, d: CalendarDay?, onClick: () -> Unit) {
    val c = palette()
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .border(1.dp, if (selected) c.accent else if (isToday) c.line else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            key.takeLast(2).toInt().toString(),
            color = if (selected) c.accent else if (weekday == 0) c.danger else if (weekday == 6) c.accent else c.fg,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (d != null && d.count > 0) Text("${d.count}개", color = c.muted, fontSize = 10.sp)
    }
}
