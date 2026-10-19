package io.github.rmia4.worktimer.ui.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rmia4.worktimer.data.Task
import io.github.rmia4.worktimer.ui.components.ErrorText
import io.github.rmia4.worktimer.ui.components.Field
import io.github.rmia4.worktimer.ui.components.Gap
import io.github.rmia4.worktimer.ui.components.Hint
import io.github.rmia4.worktimer.ui.components.Label
import io.github.rmia4.worktimer.ui.components.ModeTabs
import io.github.rmia4.worktimer.ui.components.Saved
import io.github.rmia4.worktimer.ui.components.Variant
import io.github.rmia4.worktimer.ui.components.WtButton
import io.github.rmia4.worktimer.ui.components.WtDialog
import io.github.rmia4.worktimer.ui.components.palette
import io.github.rmia4.worktimer.ui.components.rememberNow
import io.github.rmia4.worktimer.ui.main.MainViewModel
import io.github.rmia4.worktimer.util.duration
import io.github.rmia4.worktimer.util.elapsedOf
import io.github.rmia4.worktimer.util.statusLabel
import io.github.rmia4.worktimer.util.timestampLabel
import io.github.rmia4.worktimer.util.today
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.NumberFormat
import kotlin.math.roundToLong

// 고정폭 숫자(시계가 1초마다 바뀌어도 글자 폭이 흔들리지 않게).
val Tabular = TextStyle(fontFeatureSettings = "tnum")

// 기록 중인 작업의 경과 시간. 이 부분만 1초마다 다시 그린다.
@Composable
fun Elapsed(task: Task, offset: Long, color: Color, fontSize: TextUnit, modifier: Modifier = Modifier, fontWeight: FontWeight? = null) {
    val now by rememberNow(offset)
    Text(duration(elapsedOf(task, now)), modifier, color = color, fontSize = fontSize, fontWeight = fontWeight, style = Tabular)
}

// 크롬 확장 타이머 화면(웹앱 TimerPanel·TaskForm)과 같은 문구와 버튼 규칙이다.
// 기록 중인 작업이 있으면 그 작업만, 없으면 새 기록 추가 폼만 보여 준다. 기록 수정 중에는 폼만 보여 준다.
@Composable
fun TimerView(vm: MainViewModel) {
    val task = vm.task
    val edit = vm.edit
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (task != null && edit == null) ActiveTask(vm, task)
        else key(vm.formKey) { TaskForm(vm, edit) }
        ErrorText(vm.error)
    }
}

@Composable
private fun ActiveTask(vm: MainViewModel, task: Task) {
    val c = palette()
    var finishing by remember { mutableStateOf<Task?>(null) } // 종료·저장 대화상자를 열 때의 진행 작업
    // 알림의 종료·저장 버튼으로 열었으면 바로 대화상자를 띄운다.
    LaunchedEffect(vm.finishRequest) {
        vm.finishRequest?.let { if (it == task.id) finishing = task }
        vm.finishRequest = null
    }
    Column(Modifier.fillMaxWidth().background(c.active, RoundedCornerShape(8.dp)).padding(16.dp)) {
        Text("작업 스톱워치 · ${statusLabel(task.status)}", color = c.activeMuted, fontSize = 13.sp)
        Text(task.title, Modifier.padding(vertical = 4.dp), color = c.activeText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        if (task.target > 0) Text("목표 ${duration(task.target)}", color = c.activeMuted, fontSize = 13.sp)
        Elapsed(task, vm.offset, c.activeText, 40.sp, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
        Hint("${timestampLabel(task.startedAt)} 시작 · 앱을 닫아도 기록이 유지됩니다.", Modifier.padding(bottom = 12.dp), color = c.activeMuted)
        Gap {
            val running = task.status == "running"
            WtButton(
                if (running) "일시정지" else "다시 시작",
                { vm.actShow(buildJsonObject { put("action", if (running) "pause" else "resume"); put("id", task.id); put("version", task.version) }) },
                Modifier.weight(1f),
                enabled = !vm.busy,
                background = c.toggleBg,
                border = c.toggleLine,
                textColor = c.toggleText,
            )
            WtButton("종료·저장", { finishing = task }, Modifier.weight(1f), enabled = !vm.busy, background = Color.Transparent, border = c.activeOutline, textColor = Color.White)
        }
    }
    if (task.note.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().background(c.bg, RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))) {
            Column(Modifier.width(3.dp).heightIn(min = 40.dp).background(c.line)) {}
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text("업무 내용", color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(task.note, Modifier.padding(top = 2.dp), color = c.fg, fontSize = 15.sp)
            }
        }
    }
    finishing?.let { FinishDialog(vm, it) { finishing = null } }
}

// 업무 결과를 적고 작업을 끝낸다.
@Composable
private fun FinishDialog(vm: MainViewModel, task: Task, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var result by rememberSaveable { mutableStateOf(task.result ?: "") }
    var error by remember { mutableStateOf("") }
    val close = { if (!vm.busy) onClose() }
    WtDialog(close, "업무 결과 저장") {
        Hint("“${task.title}” 작업을 종료합니다. 완료한 결과를 기록해 주세요.")
        Label("업무 결과")
        Field(result, { result = it }, placeholder = "완료 결과, 산출물, 확인 사항을 입력하세요.", multiline = true, maxLength = 20000, minHeight = 160.dp)
        Hint("${NumberFormat.getInstance().format(result.length)} / 20,000", Modifier.fillMaxWidth(), textAlign = TextAlign.End)
        ErrorText(error)
        Gap {
            WtButton("취소", close, Modifier.weight(1f), Variant.Outline, enabled = !vm.busy)
            WtButton(if (vm.busy) "저장 중…" else "종료하고 저장", {
                scope.launch {
                    val failed = vm.act(buildJsonObject { put("action", "finish"); put("id", task.id); put("version", task.version); put("result", result) })
                    if (failed == null) onClose() else error = failed
                }
            }, Modifier.weight(1f), enabled = !vm.busy)
        }
    }
}

private fun minutesOk(v: String) = v.isEmpty() || v.toDoubleOrNull()?.let { it in 0.0..525600.0 } == true
private fun minutesText(ms: Long) = (Math.round(ms / 600.0) / 100.0).let { if (it % 1 == 0.0) it.toLong().toString() else it.toString() }

@Composable
private fun TaskForm(vm: MainViewModel, edit: Task?) {
    val c = palette()
    var title by rememberSaveable { mutableStateOf(edit?.title ?: "") }
    var note by rememberSaveable { mutableStateOf(edit?.note ?: "") }
    var result by rememberSaveable { mutableStateOf(edit?.result ?: "") }
    var work by rememberSaveable { mutableStateOf("note") }
    var target by rememberSaveable { mutableStateOf(if (edit != null && edit.target > 0) minutesText(edit.target) else "") }
    var minutes by rememberSaveable { mutableStateOf(if (edit != null) minutesText(edit.elapsed) else "0") }
    var manual by rememberSaveable { mutableStateOf(false) } // 소요 시간 직접 입력 펼침
    var formError by remember { mutableStateOf("") }

    // 웹앱 save(): 목표·소요 시간은 분 단위 입력을 밀리초로 바꾼다.
    fun save(run: Boolean) {
        if (title.isBlank()) return run { formError = "작업명을 입력해 주세요." }
        if (!minutesOk(target) || !minutesOk(minutes) || minutes.isEmpty()) return run { formError = "시간은 0~525600분으로 입력해 주세요." }
        formError = ""
        vm.actShow(buildJsonObject {
            put("action", if (edit != null) "edit" else "create")
            put("id", edit?.id)
            put("version", edit?.version)
            put("title", title)
            put("note", note)
            put("result", result)
            put("target", if (target.isEmpty()) 0 else (target.toDouble() * 60000).roundToLong())
            // 바로 측정을 시작하면 보고 있는 날짜와 상관없이 오늘에 기록한다.
            put("day", if (run && edit == null) today() else vm.day)
            put("elapsed", (minutes.toDouble() * 60000).roundToLong())
            put("started_at", edit?.startedAt?.let { it / 1000 * 1000 })
            put("ended_at", edit?.endedAt?.let { it / 1000 * 1000 })
            put("run", run)
        }) { vm.clearForm() }
    }

    Column(
        Modifier.fillMaxWidth().background(c.bg, RoundedCornerShape(8.dp)).border(1.dp, c.line, RoundedCornerShape(8.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (edit != null) "기록 수정" else "새 기록 추가", color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Label("작업명")
        Field(title, { title = it }, placeholder = "목표 작업 적기", maxLength = 200)
        ModeTabs(work, listOf("note" to "업무 내용", "result" to "업무 결과")) { work = it }
        if (work == "note") Field(note, { note = it }, placeholder = "작업 세부사항, 목표 적기", multiline = true, maxLength = 20000)
        else Field(result, { result = it }, placeholder = "결과, 산출물, 추후 확인 사항 적기", multiline = true, maxLength = 20000)
        Label("목표 시간 (분)")
        Field(target, { target = it }, placeholder = "선택 입력", keyboardType = KeyboardType.Decimal)
        Hint("입력하지 않으면 목표 시간이 표시되지 않습니다.")
        Text(
            "${if (manual) "▾" else "▸"} 소요 시간 직접 입력 (선택)",
            Modifier.clickable { manual = !manual }.padding(vertical = 4.dp),
            color = c.muted,
            fontSize = 13.sp,
        )
        if (manual) {
            Label("소요 시간 (분)")
            Field(minutes, { minutes = it }, keyboardType = KeyboardType.Decimal)
            Hint("직접 기록할 때 입력하세요. 측정 시작 시에는 0부터 시작합니다.")
        }
        Gap {
            WtButton(if (edit != null) "수정 후 측정 재개" else "측정 시작", { save(true) }, Modifier.weight(1f), enabled = !vm.busy && vm.task == null && title.isNotBlank())
            WtButton(if (edit != null) "수정 저장" else "기록 저장", { save(false) }, Modifier.weight(1f), Variant.Outline, enabled = !vm.busy)
            if (edit != null) WtButton("취소", vm::clearForm, Modifier.weight(1f), Variant.Outline)
        }
        ErrorText(formError)
        Saved(vm.message)
    }
}
