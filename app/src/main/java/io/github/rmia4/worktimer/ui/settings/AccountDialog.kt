package io.github.rmia4.worktimer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rmia4.worktimer.data.Api
import io.github.rmia4.worktimer.data.errorText
import io.github.rmia4.worktimer.ui.components.ErrorText
import io.github.rmia4.worktimer.ui.components.Field
import io.github.rmia4.worktimer.ui.components.Gap
import io.github.rmia4.worktimer.ui.components.Hint
import io.github.rmia4.worktimer.ui.components.Label
import io.github.rmia4.worktimer.ui.components.Saved
import io.github.rmia4.worktimer.ui.components.Variant
import io.github.rmia4.worktimer.ui.components.WtButton
import io.github.rmia4.worktimer.ui.components.WtDialog
import io.github.rmia4.worktimer.ui.components.palette
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// 크롬 확장 계정 설정(웹앱 settings-dialog.tsx의 계정 탭)과 같은 요청이다. 매 요청에 확인한 현재 비밀번호를 함께 보낸다.
// 비밀번호가 틀려도 401이 오므로 authed() 대신 call()로 보내 오류 문구만 보여 준다.
private const val DELETE_ACCOUNT_PHRASE = "계정 삭제"
private val DELETE_LABELS = linkedMapOf(
    "tasks" to ("업무일지 전체 삭제" to "모든 업무일지 기록과 측정 시간이"),
    "memos" to ("전체 메모 삭제" to "모든 메모가"),
    "daily-memos" to ("전체 일일메모 삭제" to "모든 날짜의 일일메모가"),
)

// 확인을 마친 현재 비밀번호는 이 대화상자가 열려 있는 동안만 메모리에 둔다(닫으면 사라진다).
@Composable
fun AccountDialog(api: Api, onClose: () -> Unit, onDeleted: (String) -> Unit, onAccountDeleted: () -> Unit) {
    val c = palette()
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) } // 확인 중인 삭제(종류 또는 account)
    var phrase by remember { mutableStateOf("") }

    fun request(pw: String = password, body: JsonObjectBuilder.() -> Unit, onOk: () -> Unit) {
        if (busy) return
        busy = true
        error = ""
        message = ""
        scope.launch {
            try {
                val r = api.call("/api/account", "POST", buildJsonObject { body(); put("password", pw) })
                if (!r.ok) error = r.error ?: "처리하지 못했습니다." else onOk()
            } catch (e: Exception) {
                error = errorText(e)
            } finally {
                busy = false
            }
        }
    }

    fun verify() {
        val pw = current
        current = ""
        request(pw, { put("action", "verify") }) { password = pw }
    }

    fun changePassword() {
        if (next.length < 8) return run { error = "비밀번호는 8자 이상 입력해 주세요." }
        if (next != confirm) return run { error = "새 비밀번호가 일치하지 않습니다." }
        request(body = { put("action", "change-password"); put("newPassword", next); put("confirmation", confirm) }) {
            password = next
            next = ""
            confirm = ""
            message = "비밀번호가 변경되었습니다. 다른 기기는 로그아웃됩니다."
        }
    }

    // 삭제는 확인 대화상자를 한 번 더 거친다. 계정 삭제는 확인 문구까지 입력해야 한다.
    fun doDelete() {
        val kind = deleting ?: return
        deleting = null
        if (kind == "account") {
            // 세션 쿠키가 지워졌으므로 로그인 화면으로 돌아간다.
            request(body = { put("action", "delete-account"); put("confirmation", phrase) }) { onAccountDeleted() }
            return
        }
        request(body = { put("action", "delete-$kind") }) {
            message = "${DELETE_LABELS.getValue(kind).first}가 완료되었습니다."
            onDeleted(kind)
        }
    }

    deleting?.let { kind ->
        val account = kind == "account"
        WtDialog({ deleting = null }, if (account) "계정 삭제" else DELETE_LABELS.getValue(kind).first) {
            Hint(
                if (account) "계정과 모든 업무일지·메모·일일메모가 영구 삭제되며 되돌릴 수 없습니다. 계속하려면 아래에 ‘$DELETE_ACCOUNT_PHRASE’를 입력해 주세요."
                else "${DELETE_LABELS.getValue(kind).second} 영구 삭제되며 되돌릴 수 없습니다.",
            )
            if (account) {
                Label("확인 문구")
                Field(phrase, { phrase = it }, placeholder = DELETE_ACCOUNT_PHRASE, maxLength = 20)
            }
            Gap {
                WtButton("취소", { deleting = null }, Modifier.weight(1f), Variant.Outline)
                WtButton(if (account) "계정 영구 삭제" else "삭제", ::doDelete, Modifier.weight(1f), Variant.DangerFill, enabled = !account || phrase == DELETE_ACCOUNT_PHRASE)
            }
        }
        return
    }

    WtDialog({ if (!busy) onClose() }, "계정 설정", closeButton = true) {
        if (password.isEmpty()) {
            Hint("계속하려면 현재 비밀번호를 입력해 주세요.")
            Label("현재 비밀번호")
            Field(current, { current = it }, password = true, maxLength = 128, onDone = ::verify)
            WtButton("확인", ::verify, Modifier.fillMaxWidth(), enabled = !busy && current.isNotEmpty())
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("비밀번호 변경", color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Hint("비밀번호를 바꾸면 다른 기기는 로그아웃됩니다.")
                Label("새 비밀번호")
                Field(next, { next = it }, password = true, maxLength = 128)
                Label("새 비밀번호 확인")
                Field(confirm, { confirm = it }, password = true, maxLength = 128)
                WtButton("비밀번호 변경", ::changePassword, Modifier.fillMaxWidth(), enabled = !busy && next.isNotEmpty() && confirm.isNotEmpty())
                Text("데이터 삭제", Modifier.padding(top = 8.dp), color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                for ((kind, labels) in DELETE_LABELS) {
                    WtButton(labels.first, { phrase = ""; deleting = kind }, Modifier.fillMaxWidth(), Variant.Danger, enabled = !busy)
                }
                Text("계정 삭제", color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                WtButton("계정 삭제", { phrase = ""; deleting = "account" }, Modifier.fillMaxWidth(), Variant.Danger, enabled = !busy)
            }
        }
        ErrorText(error)
        Saved(message)
    }
}
