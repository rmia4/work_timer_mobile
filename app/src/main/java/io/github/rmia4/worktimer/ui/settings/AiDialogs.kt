package io.github.rmia4.worktimer.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import io.github.rmia4.worktimer.data.Api
import io.github.rmia4.worktimer.data.Connection
import io.github.rmia4.worktimer.data.ORIGIN
import io.github.rmia4.worktimer.data.errorText
import io.github.rmia4.worktimer.data.json
import io.github.rmia4.worktimer.ui.components.ErrorText
import io.github.rmia4.worktimer.ui.components.Gap
import io.github.rmia4.worktimer.ui.components.Hint
import io.github.rmia4.worktimer.ui.components.Saved
import io.github.rmia4.worktimer.ui.components.Variant
import io.github.rmia4.worktimer.ui.components.WtButton
import io.github.rmia4.worktimer.ui.components.WtDialog
import io.github.rmia4.worktimer.ui.components.palette
import io.github.rmia4.worktimer.ui.summary.copyText
import io.github.rmia4.worktimer.util.KST
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDateTime

// 크롬 확장의 AI 연결 메뉴(웹앱 workspace-parts.tsx)와 연결된 앱 목록(settings-dialog.tsx). 고른 항목의 대화상자로 바꿔 연다.
@Composable
fun AiDialogs(api: Api, onClose: () -> Unit) {
    var kind by remember { mutableStateOf("menu") }
    when (kind) {
        "claude" -> ClaudeDialog(onClose)
        "codex" -> CodexDialog(onClose)
        "connections" -> ConnectionsDialog(api, onClose)
        else -> WtDialog(onClose, "AI 연결", closeButton = true) {
            WtButton("Claude에 연결", { kind = "claude" }, Modifier.fillMaxWidth(), Variant.Outline)
            WtButton("Codex에 연결", { kind = "codex" }, Modifier.fillMaxWidth(), Variant.Outline)
            WtButton("연결된 앱", { kind = "connections" }, Modifier.fillMaxWidth(), Variant.Outline)
        }
    }
}

// claude.ai의 커스텀 커넥터 추가 화면을 이름·MCP 주소를 채운 채로 브라우저에서 연다.
@Composable
private fun ClaudeDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    WtDialog(onClose, "Claude에 연결", closeButton = true) {
        Hint("claude.ai의 커스텀 커넥터 추가 화면을 Work Timer 주소를 채운 채로 엽니다. 연결을 추가한 뒤 Work Timer 로그인·동의 화면이 열리면 허용합니다.")
        WtButton("claude.ai 열기", {
            val params = "modal=add-custom-connector&connectorName=${Uri.encode("Work Timer")}&connectorUrl=${Uri.encode("$ORIGIN/api/mcp")}"
            context.startActivity(Intent(Intent.ACTION_VIEW, "https://claude.ai/customize/connectors?$params".toUri()))
        }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun CodexDialog(onClose: () -> Unit) {
    val c = palette()
    val context = LocalContext.current
    var error by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf("") }
    LaunchedEffect(copied) {
        if (copied.isNotEmpty()) {
            delay(2000)
            copied = ""
        }
    }
    // Codex 앱 딥링크. 이 기기에 Codex 앱이 없으면 열리지 않는다.
    fun open(url: String) {
        error = ""
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            error = "Codex 앱을 열지 못했습니다. Codex 앱이 설치된 기기에서 진행해 주세요."
        }
    }

    @Composable
    fun Step(n: Int, content: @Composable () -> Unit) = Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$n.", color = c.fg, fontSize = 13.sp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }

    // Codex "마켓플레이스 추가"에 입력할 값을 복사한다.
    @Composable
    fun Value(label: String, value: String) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.widthIn(min = 52.dp), color = c.muted, fontSize = 13.sp)
        Text(
            value,
            Modifier.weight(1f).background(c.page, RoundedCornerShape(4.dp)).border(1.dp, c.line, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            color = c.fg,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
        )
        WtButton(if (copied == value) "복사했습니다" else "복사", { copyText(context, value); copied = value }, variant = Variant.Outline, small = true)
    }

    WtDialog(onClose, "Codex에 연결", closeButton = true) {
        Hint("Codex 앱에 Work Timer 마켓플레이스를 추가한 뒤 플러그인을 설치합니다.")
        Step(1) {
            Text("Codex 앱에서 플러그인 화면을 엽니다.", color = c.fg, fontSize = 13.sp, lineHeight = 19.sp)
            WtButton("Codex 플러그인 화면 열기", { open("codex://plugins") }, variant = Variant.Outline, small = true)
        }
        Step(2) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("관리 > 마켓플레이스") }
                    append(" 탭에서 ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("마켓플레이스 추가") }
                    append("를 누르고 아래 값을 입력합니다. Sparse 경로는 비워 둡니다.")
                },
                color = c.fg,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Value("출처", "rmia4/work_timer")
            Value("Git ref", "plugin/codex")
        }
        Step(3) {
            Text("Work Timer 플러그인을 열어 설치하고, Work Timer 로그인·동의 화면이 열리면 허용합니다.", color = c.fg, fontSize = 13.sp, lineHeight = 19.sp)
            WtButton("Work Timer 플러그인 열기", { open("codex://plugins/install/work-timer?marketplace=work-timer-local") }, small = true)
        }
        ErrorText(error)
    }
}

private fun connectionTime(t: Long): String {
    val d = LocalDateTime.ofInstant(Instant.ofEpochMilli(t), KST)
    return "${d.year}. ${d.monthValue}. ${d.dayOfMonth}. ${d.hour.toString().padStart(2, '0')}:${d.minute.toString().padStart(2, '0')}"
}

@Composable
private fun ConnectionsDialog(api: Api, onClose: () -> Unit) {
    val c = palette()
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<Connection>?>(null) }
    var error by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<Connection?>(null) } // 해제를 확인 중인 연결

    LaunchedEffect(Unit) {
        try {
            val r = api.authed("/api/connections") ?: return@LaunchedEffect
            val connections = r.data["connections"]
            if (!r.ok || connections == null) throw io.github.rmia4.worktimer.data.ApiException(r.error ?: "연결된 앱을 불러오지 못했습니다.")
            list = json.decodeFromJsonElement<List<Connection>>(connections)
        } catch (e: Exception) {
            list = emptyList()
            error = errorText(e, "연결된 앱을 불러오지 못했습니다.")
        }
    }

    fun disconnect() {
        val t = target ?: return
        target = null
        if (busy) return
        busy = true
        error = ""
        message = ""
        scope.launch {
            try {
                val r = api.authed("/api/connections", "POST", buildJsonObject { put("action", "disconnect"); put("id", t.id) }) ?: return@launch
                // 이미 해제된 연결(404)도 목록에서 빼면 된다.
                if (!r.ok && r.status != 404) error = r.error ?: "처리하지 못했습니다."
                else {
                    list = list?.filter { it.id != t.id }
                    message = "${t.name} 연결을 해제했습니다."
                }
            } catch (e: Exception) {
                error = errorText(e, "처리하지 못했습니다.")
            } finally {
                busy = false
            }
        }
    }

    target?.let { t ->
        WtDialog({ target = null }, "연결 해제") {
            Hint("${t.name} 연결을 해제할까요? 해제하면 그 앱은 더 이상 업무 기록에 접근할 수 없으며, 다시 쓰려면 새로 연결해야 합니다.")
            Gap {
                WtButton("취소", { target = null }, Modifier.weight(1f), Variant.Outline)
                WtButton("연결 해제", ::disconnect, Modifier.weight(1f), Variant.DangerFill)
            }
        }
        return
    }
    WtDialog(onClose, "연결된 앱", closeButton = true) {
        val items = list
        if (items == null) Hint("불러오는 중…") else if (items.isEmpty() && error.isEmpty()) Hint("연결된 앱이 없습니다.")
        items?.forEach { x ->
            Row(
                Modifier.fillMaxWidth().border(1.dp, c.line, RoundedCornerShape(6.dp)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(x.name, color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    listOfNotNull(
                        x.host,
                        "권한: ${x.scopes.joinToString(", ")}",
                        "연결 ${connectionTime(x.created)} · 마지막 사용 ${x.lastUsed?.let(::connectionTime) ?: "없음"}",
                    ).forEach { Text(it, color = c.muted, fontSize = 12.sp) }
                }
                WtButton("연결 해제", { target = x }, variant = Variant.Danger, small = true, enabled = !busy)
            }
        }
        ErrorText(error)
        Saved(message)
    }
}
