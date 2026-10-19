package io.github.rmia4.worktimer.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rmia4.worktimer.data.Task
import io.github.rmia4.worktimer.ui.components.ErrorText
import io.github.rmia4.worktimer.ui.components.Gap
import io.github.rmia4.worktimer.ui.components.Hint
import io.github.rmia4.worktimer.ui.components.UnderlineTabs
import io.github.rmia4.worktimer.ui.components.Variant
import io.github.rmia4.worktimer.ui.components.WtButton
import io.github.rmia4.worktimer.ui.components.WtCard
import io.github.rmia4.worktimer.ui.components.WtDialog
import io.github.rmia4.worktimer.ui.components.palette
import io.github.rmia4.worktimer.ui.main.MainViewModel
import io.github.rmia4.worktimer.ui.memo.MemoBoard
import io.github.rmia4.worktimer.ui.timer.Elapsed
import io.github.rmia4.worktimer.ui.timer.Tabular
import io.github.rmia4.worktimer.util.duration
import io.github.rmia4.worktimer.util.shiftDay
import io.github.rmia4.worktimer.util.statusLabel
import io.github.rmia4.worktimer.util.timeLabel
import io.github.rmia4.worktimer.util.today
import io.github.rmia4.worktimer.util.weekday
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// 크롬 확장 일지 화면: 날짜를 옮기거나 캘린더에서 고른 날짜의 기록과 일일 메모를 본다.
@Composable
fun LogView(vm: MainViewModel) {
    val c = palette()
    var mode by rememberSaveable { mutableStateOf("records") }
    var calendar by rememberSaveable { mutableStateOf(false) }
    var remove by remember { mutableStateOf<Task?>(null) } // 삭제 확인 중인 기록
    val day = vm.day
    val tasks = vm.tasks.filter { it.day == day }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UnderlineTabs(mode, listOf("records" to "업무 일지", "memos" to "일일 메모")) { mode = it }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            WtButton("‹", { vm.chooseDay(shiftDay(day, -1)) }, variant = Variant.Outline, small = true, enabled = !vm.busy)
            Text("$day (${weekday(day)})", Modifier.padding(horizontal = 4.dp), color = c.fg, fontSize = 14.sp, style = Tabular)
            WtButton("›", { vm.chooseDay(shiftDay(day, 1)) }, variant = Variant.Outline, small = true, enabled = !vm.busy)
            Spacer(Modifier.weight(1f))
            WtButton("오늘", { vm.chooseDay(today()) }, variant = Variant.Outline, small = true, enabled = !vm.busy)
            WtButton("캘린더", { calendar = true }, variant = Variant.Outline, small = true)
        }

        if (mode == "records") {
            when {
                vm.loading -> Hint("기록을 불러오는 중입니다.")
                tasks.isEmpty() -> Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("아직 기록이 없습니다.", color = c.fg, fontWeight = FontWeight.SemiBold)
                    Hint("작업을 시작하거나 완료한 업무를 남겨보세요.", Modifier.padding(top = 4.dp))
                }
                else -> tasks.forEach { t -> Record(t, vm, onEdit = { vm.startEdit(t) }, onRemove = { remove = t }) }
            }
        } else {
            MemoBoard(vm.dailyMemos(day), collapsible = false, emptyText = "선택한 날짜에 메모를 남겨보세요.", scroll = false)
        }
        ErrorText(vm.error)
    }

    if (calendar) {
        WtDialog({ calendar = false }) {
            Calendar(vm, day) { vm.chooseDay(it); calendar = false }
            WtButton("닫기", { calendar = false }, Modifier.fillMaxWidth(), Variant.Outline)
        }
    }
    remove?.let { t ->
        WtDialog({ remove = null }, "이 기록을 삭제하시겠습니까?") {
            Hint("“${t.title}”의 업무 내용과 측정 시간이 삭제됩니다.")
            Gap {
                WtButton("취소", { remove = null }, Modifier.weight(1f), Variant.Outline)
                WtButton("삭제", {
                    remove = null
                    vm.actShow(buildJsonObject { put("action", "delete"); put("id", t.id); put("version", t.version) })
                }, Modifier.weight(1f))
            }
        }
    }
}

// 웹앱 RecordList(카드형)와 같은 내용이다. 소요 시간만 1초마다 다시 그린다.
@Composable
private fun Record(t: Task, vm: MainViewModel, onEdit: () -> Unit, onRemove: () -> Unit) {
    val c = palette()
    WtCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t.title, Modifier.weight(1f), color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (t.target > 0) Column {
                    Text("목표", color = c.muted, fontSize = 11.sp)
                    Text(duration(t.target), color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.Bold, style = Tabular)
                }
                Column {
                    Text("소요", color = c.muted, fontSize = 11.sp)
                    Elapsed(t, vm.offset, c.fg, 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            "${statusLabel(t.status)} · 시작 ${timeLabel(t.startedAt)} · 종료 ${if (t.done) timeLabel(t.endedAt) else "진행 중"}",
            Modifier.padding(vertical = 4.dp),
            color = c.muted,
            fontSize = 13.sp,
        )
        Journal("업무 내용", t.note, c.line, c.muted)
        Journal("업무 결과", t.result, c.accent, c.accent)
        if (t.done) {
            Gap {
                WtButton("수정", onEdit, Modifier.weight(1f).padding(top = 8.dp), Variant.Outline, enabled = !vm.busy, small = true)
                WtButton("삭제", onRemove, Modifier.weight(1f).padding(top = 8.dp), Variant.Outline, enabled = !vm.busy, small = true)
            }
        }
    }
}

@Composable
private fun Journal(label: String, text: String?, line: Color, labelColor: Color) {
    if (text.isNullOrEmpty()) return
    Row(Modifier.padding(top = 8.dp)) {
        Box(Modifier.width(3.dp).height(36.dp).background(line))
        Column(Modifier.padding(start = 8.dp)) {
            Text(label, color = labelColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(text, Modifier.padding(top = 2.dp), color = palette().fg, fontSize = 14.sp)
        }
    }
}
