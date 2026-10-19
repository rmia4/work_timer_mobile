package io.github.rmia4.worktimer.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rmia4.worktimer.data.Api
import io.github.rmia4.worktimer.data.errorText
import io.github.rmia4.worktimer.ui.components.ErrorText
import io.github.rmia4.worktimer.ui.components.Field
import io.github.rmia4.worktimer.ui.components.Hint
import io.github.rmia4.worktimer.ui.components.Label
import io.github.rmia4.worktimer.ui.components.ModeTabs
import io.github.rmia4.worktimer.ui.components.WtButton
import io.github.rmia4.worktimer.ui.components.palette
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// 크롬 확장(웹앱 login.tsx)의 로그인·회원가입 화면과 같은 규칙이다.
// onSignedIn은 로그인 쿠키가 실제로 저장됐는지 확인해 결과를 돌려준다.
@Composable
fun LoginScreen(api: Api, onSignedIn: suspend () -> Boolean) {
    val c = palette()
    val scope = rememberCoroutineScope()
    var signup by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }

    fun changeMode(next: Boolean) {
        if (next == signup) return
        signup = next
        password = ""
        confirmation = ""
        error = ""
    }

    fun submit() {
        if (busy) return
        if (username.isEmpty() || password.isEmpty() || (signup && confirmation.isEmpty())) {
            error = "아이디와 비밀번호를 입력해 주세요."
            return
        }
        busy = true
        error = ""
        scope.launch {
            try {
                val body = buildJsonObject {
                    put("username", username)
                    put("password", password)
                    put("confirmation", confirmation)
                }
                val r = api.call(if (signup) "/api/auth/signup" else "/api/auth", "POST", body)
                if (!r.ok) error = r.error ?: if (signup) "가입하지 못했습니다." else "로그인하지 못했습니다."
                else {
                    password = ""
                    confirmation = ""
                    // 로그인은 됐는데 바로 401이면 기기가 로그인 쿠키를 저장하지 않은 것이다.
                    if (!onSignedIn()) error = "로그인 정보를 저장하지 못했습니다. 다시 시도해 주세요."
                }
            } catch (e: Exception) {
                error = errorText(e)
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.page)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.CenterVertically),
    ) {
        Text("Work Timer", Modifier.fillMaxWidth().padding(bottom = 16.dp), color = c.fg, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        ModeTabs(signup, listOf(false to "로그인", true to "회원가입"), ::changeMode)
        Hint(if (signup) "아이디는 영문 소문자·숫자·밑줄 4~20자, 비밀번호는 8자 이상 입력해 주세요." else "아이디와 비밀번호를 입력해 주세요.")
        Label("아이디")
        Field(username, { username = it }, maxLength = 20)
        Label("비밀번호")
        Field(password, { password = it }, password = true, maxLength = 128, onDone = if (signup) null else ::submit)
        if (signup) {
            Label("비밀번호 확인")
            Field(confirmation, { confirmation = it }, password = true, maxLength = 128, onDone = ::submit)
        }
        WtButton(if (signup) "가입하기" else "로그인", ::submit, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !busy)
        ErrorText(error)
    }
}
