package io.github.rmia4.worktimer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rmia4.worktimer.notify.TaskNotification
import io.github.rmia4.worktimer.ui.components.palette
import io.github.rmia4.worktimer.ui.login.LoginScreen
import io.github.rmia4.worktimer.ui.main.MainScreen
import io.github.rmia4.worktimer.ui.theme.WorkTimerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as WorkTimerApp
        if (savedInstanceState == null) openFinish(intent)
        setContent {
            WorkTimerTheme(app.prefs) {
                val c = palette()
                // 상태 표시줄 아이콘 색을 테마에 맞춘다.
                LaunchedEffect(c.dark) {
                    val style = if (c.dark) SystemBarStyle.dark(c.bg.toArgb()) else SystemBarStyle.light(c.bg.toArgb(), c.fg.toArgb())
                    enableEdgeToEdge(style, style)
                }
                ExitOnDoubleBack()
                Box(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding()) { Root(app) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFinish(intent)
    }

    // 알림의 종료·저장 버튼으로 열었으면 타이머 화면에 업무 결과 저장 대화상자를 띄우게 한다.
    private fun openFinish(intent: Intent) {
        intent.getStringExtra(TaskNotification.FINISH)?.let { (application as WorkTimerApp).finishRequest.value = it }
    }

    // 뒤로 가기를 처음 누르면 알리고, 2초 안에 다시 누르면 앱을 닫는다. 대화상자·메뉴가 열려 있으면 그쪽이 먼저 닫힌다.
    @Composable
    private fun ExitOnDoubleBack() {
        val last = remember { longArrayOf(0) }
        BackHandler {
            val now = System.currentTimeMillis()
            if (now - last[0] < 2000) finish()
            else {
                last[0] = now
                Toast.makeText(this, "한 번 더 누르면 종료됩니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
private fun Root(app: WorkTimerApp) {
    val signIn by app.signIn.collectAsStateWithLifecycle()
    // 로그인 쿠키가 아직 유효한지 확인한다(유효하면 세션도 늘어난다). 연결이 안 되면 일단 화면을 열고 오류는 각 화면이 보여 준다.
    suspend fun check(): Boolean {
        val r = runCatching { app.api.call("/api/auth", "PATCH") }.getOrNull()
        val ok = r == null || r.status != 401
        app.signIn.value = if (ok) SignIn.SignedIn else SignIn.SignedOut
        return ok
    }
    LaunchedEffect(Unit) { check() }
    // 로그아웃하면 다음 로그인 때 이전 계정의 화면 상태를 쓰지 않도록 메인 화면 상태를 새로 만든다.
    var generation by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(signIn) { if (signIn == SignIn.SignedOut) generation++ }
    when (signIn) {
        SignIn.Checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = palette().accent) }
        SignIn.SignedOut -> LoginScreen(app.api, ::check)
        SignIn.SignedIn -> MainScreen(app, "main$generation")
    }
}
