package io.github.rmia4.worktimer.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import io.github.rmia4.worktimer.data.ORIGIN
import io.github.rmia4.worktimer.notify.NotifyMode
import io.github.rmia4.worktimer.notify.TaskNotification
import io.github.rmia4.worktimer.ui.components.Hint
import io.github.rmia4.worktimer.ui.components.ModeTabs
import io.github.rmia4.worktimer.ui.components.WtCard
import io.github.rmia4.worktimer.ui.components.palette
import io.github.rmia4.worktimer.ui.main.MainViewModel
import io.github.rmia4.worktimer.ui.theme.LocalTheme
import io.github.rmia4.worktimer.ui.theme.ThemeMode

// 상단 설정 버튼으로 여는 설정 페이지. 크롬 확장 설정 메뉴(웹앱 workspace-parts.tsx)의 항목을 한 화면에 모았다.
// 뒤로 가기나 왼쪽 위 화살표로 닫는다.
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = palette()
    val theme = LocalTheme.current
    val context = LocalContext.current
    var ai by rememberSaveable { mutableStateOf(false) }
    var account by rememberSaveable { mutableStateOf(false) }
    var allowed by remember { mutableStateOf(TaskNotification.allowed(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = it
        vm.notifyTask()
    }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(c.page)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).background(c.bg).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "뒤로" },
                contentAlignment = Alignment.Center,
            ) { Text("‹", color = c.muted, fontSize = 26.sp) }
            Text("설정", color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider(color = c.line)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Section("화면") {
                ModeTabs(
                    theme.mode,
                    listOf(ThemeMode.Light to "라이트 모드", ThemeMode.Dark to "다크 모드", ThemeMode.System to "기기 설정"),
                    theme.select,
                )
            }
            Section("알림") {
                ModeTabs(
                    vm.notifyMode,
                    listOf(NotifyMode.Off to "알림 없음", NotifyMode.Mine to "모바일 실행만", NotifyMode.All to "모두 알림"),
                ) {
                    vm.selectNotify(it)
                    if (it != NotifyMode.Off && !allowed && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                Hint("기록 중인 작업을 알림창에 표시합니다. 다른 곳에서 시작·변경한 작업은 앱을 열 때 반영됩니다.")
                if (vm.notifyMode != NotifyMode.Off && !allowed) Hint("알림 권한이 꺼져 있습니다.", color = c.danger)
            }
            Section("연결") {
                Item("AI 연결") { ai = true }
                Item("웹페이지 방문") { context.startActivity(Intent(Intent.ACTION_VIEW, ORIGIN.toUri())) }
            }
            Section("계정") {
                Item("계정 설정") { account = true }
                Item("로그아웃", enabled = !vm.busy) { vm.logout() }
            }
        }
    }

    if (ai) AiDialogs(vm.api) { ai = false }
    if (account) AccountDialog(vm.api, onClose = { account = false }, onDeleted = vm::onDeleted, onAccountDeleted = vm::signedOut)
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = palette()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, Modifier.padding(horizontal = 4.dp), color = c.muted, fontSize = 13.sp)
        WtCard(content = content)
    }
}

@Composable
private fun Item(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).alpha(if (enabled) 1f else 0.5f).clickable(enabled, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), color = palette().fg, fontSize = 15.sp)
    }
}
