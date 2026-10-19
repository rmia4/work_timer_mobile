package io.github.rmia4.worktimer.ui.summary

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.github.rmia4.worktimer.data.errorText
import io.github.rmia4.worktimer.ui.components.ErrorText
import io.github.rmia4.worktimer.ui.components.Gap
import io.github.rmia4.worktimer.ui.components.Hint
import io.github.rmia4.worktimer.ui.components.Label
import io.github.rmia4.worktimer.ui.components.Variant
import io.github.rmia4.worktimer.ui.components.WtButton
import io.github.rmia4.worktimer.ui.components.WtDialog
import io.github.rmia4.worktimer.ui.components.palette
import io.github.rmia4.worktimer.ui.components.rememberNow
import io.github.rmia4.worktimer.ui.main.MainViewModel
import io.github.rmia4.worktimer.ui.timer.Tabular
import io.github.rmia4.worktimer.util.duration
import io.github.rmia4.worktimer.util.summaryPrompt
import io.github.rmia4.worktimer.util.today
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// 크롬 확장 요약 화면: 일지에서 고른 날짜의 측정 시간과 하루 활동 시계. 시계는 1분마다 다시 그린다.
@Composable
fun SummaryView(vm: MainViewModel) {
    val c = palette()
    val now by rememberNow(vm.offset, 60_000)
    var csv by rememberSaveable { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Segment?>(null) } // 활동 시계에서 누른 구간(다른 탭으로 가면 지워진다)
    val day = vm.day
    val segments = activitySegments(vm.tasks, day, now)
    val list = vm.tasks.filter { it.day == day }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // 빈 곳을 누르면 시계 구간 설명을 닫는다. 버튼·구간은 자기 누름을 먼저 받는다.
            .pointerInput(Unit) { detectTapGestures { picked = null } }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(duration(segments.sumOf { it.end - it.start }), color = c.fg, fontSize = 30.sp, style = Tabular)
            WtButton("CSV", { csv = true }, variant = Variant.Outline, small = true)
        }
        Text(
            "${if (day == today()) "오늘" else day} 측정 시간 · 작업 ${list.size}개 · 완료 ${list.count { it.done }}개",
            color = c.muted,
            fontSize = 13.sp,
        )
        ActivityClock(segments, now, picked) { picked = it }
    }
    if (csv) CsvDialog(vm, day) { csv = false }
}

private val DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")
private fun csvInvalid(from: String, to: String): Boolean {
    if (!DATE.matches(from) || !DATE.matches(to)) return true
    val a = runCatching { LocalDate.parse(from) }.getOrNull() ?: return true
    val b = runCatching { LocalDate.parse(to) }.getOrNull() ?: return true
    return a > b || ChronoUnit.DAYS.between(a, b) + 1 > 366
}
private val SCOPES = listOf("time" to "시간 기록만", "all" to "전체", "detail" to "세부내용만")

fun copyText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Work Timer", text))
}

// 웹앱 stats-dialog.tsx의 데이터 내보내기와 같은 규칙이다. 기본 기간은 보고 있는 날짜의 달이다.
// 확장은 파일을 내려받지만 앱은 CSV 파일을 만들어 공유 시트로 넘긴다(저장·메일·메신저 등).
@Composable
private fun CsvDialog(vm: MainViewModel, day: String, onClose: () -> Unit) {
    val c = palette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val month = YearMonth.parse(day.take(7))
    var from by rememberSaveable { mutableStateOf(month.atDay(1).toString()) }
    var to by rememberSaveable { mutableStateOf(month.atEndOfMonth().toString()) }
    var exportScope by rememberSaveable { mutableStateOf("time") }
    var exporting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    val invalid = csvInvalid(from, to)
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    fun exportCsv() {
        if (exporting || invalid) return
        exporting = true
        error = ""
        scope.launch {
            try {
                val bytes = vm.api.download("/api/export?from=$from&to=$to&scope=$exportScope") ?: return@launch
                val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                val file = File(dir, "work-timer_${from}_$to.csv").apply { writeBytes(bytes) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                val send = Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, "CSV 저장·공유"))
            } catch (e: Exception) {
                error = errorText(e, "데이터를 내보내지 못했습니다.")
            } finally {
                exporting = false
            }
        }
    }

    WtDialog(onClose, "데이터 내보내기", closeButton = true) {
        Hint("선택한 기간의 업무 기록을 CSV 파일로 저장합니다. (최대 366일)")
        Label("시작일")
        DateField(from) { from = it }
        Label("종료일")
        DateField(to) { to = it }
        Label("내보내기 범위")
        FlowRow(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for ((value, text) in SCOPES) {
                val on = exportScope == value
                Row(Modifier.clickable(role = Role.RadioButton) { exportScope = value }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(20.dp).border(2.dp, if (on) c.accent else c.line, CircleShape), contentAlignment = Alignment.Center) {
                        if (on) Box(Modifier.size(10.dp).background(c.accent, CircleShape))
                    }
                    Text(text, Modifier.padding(start = 6.dp), color = c.fg, fontSize = 14.sp)
                }
            }
        }
        Hint("세부내용(업무 내용·결과)이 들어가는 범위를 고르면 파일 크기와 AI 작업량이 늘어납니다.")
        Hint("CSV를 AI에 첨부할 때 같은 범위로 복사한 프롬프트를 함께 붙여넣으세요.")
        ErrorText(if (invalid) "기간을 확인해 주세요. (최대 366일)" else "")
        ErrorText(error)
        Gap {
            WtButton("취소", onClose, Modifier.weight(1f), Variant.Outline)
            WtButton(if (exporting) "내보내는 중…" else "CSV 저장·공유", ::exportCsv, Modifier.weight(1f), enabled = !exporting && !invalid)
        }
        // 웹앱 copyPrompt(): 고른 기간·범위에 맞는 AI 요약 프롬프트를 복사하고 2초 동안 알린다.
        WtButton(if (copied) "복사했습니다" else "AI 요약 프롬프트 복사", {
            copyText(context, summaryPrompt(from, to, exportScope))
            copied = true
        }, Modifier.fillMaxWidth(), Variant.Outline, enabled = !invalid)
    }
}

// 날짜(YYYY-MM-DD)를 누르면 달력 창을 띄워 고른다.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(value: String, onChange: (String) -> Unit) {
    val c = palette()
    var open by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .border(1.dp, c.line, RoundedCornerShape(6.dp))
            .clickable(role = Role.Button) { open = true }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) { Text(value, color = c.fg, fontSize = 15.sp, style = Tabular) }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(value).toEpochDay() * 86_400_000)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { onChange(LocalDate.ofEpochDay(it / 86_400_000).toString()) }
                    open = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton({ open = false }) { Text("취소") } },
        ) { DatePicker(state) }
    }
}
