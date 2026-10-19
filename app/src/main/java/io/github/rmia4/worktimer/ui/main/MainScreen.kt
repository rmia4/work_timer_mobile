package io.github.rmia4.worktimer.ui.main

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rmia4.worktimer.WorkTimerApp
import io.github.rmia4.worktimer.data.ORIGIN
import io.github.rmia4.worktimer.ui.components.GearIcon
import io.github.rmia4.worktimer.ui.components.palette
import io.github.rmia4.worktimer.ui.log.LogView
import io.github.rmia4.worktimer.ui.memo.MemoBoard
import io.github.rmia4.worktimer.ui.settings.AccountDialog
import io.github.rmia4.worktimer.ui.settings.AiDialogs
import io.github.rmia4.worktimer.ui.summary.SummaryView
import io.github.rmia4.worktimer.ui.theme.LocalTheme
import io.github.rmia4.worktimer.ui.timer.TimerView
import kotlinx.coroutines.delay

@Composable
fun MainScreen(app: WorkTimerApp, key: String) {
    val vm = viewModel(key = key) { MainViewModel(app) }
    val c = palette()
    val theme = LocalTheme.current
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var menu by rememberSaveable { mutableStateOf(false) }
    var ai by rememberSaveable { mutableStateOf(false) }
    var account by rememberSaveable { mutableStateOf(false) }
    // 탭을 오가도 각 탭의 입력 내용(rememberSaveable)을 지킨다.
    val tabs = rememberSaveableStateHolder()

    // 앱을 열 때·앞으로 돌아올 때 세션을 늘리고 다시 불러온다. 10초마다 다시 불러와 다른 기기의 변경을 반영한다.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.renewSession()
        vm.load()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flushMemos() }
    LaunchedEffect(vm) {
        while (true) {
            delay(10_000)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && !vm.busy) vm.load()
        }
    }

    Column(Modifier.fillMaxSize().background(c.page)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).background(c.bg).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(vm.view.title, Modifier.weight(1f).padding(horizontal = 8.dp), color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Box {
                Box(
                    Modifier
                        .size(44.dp)
                        .clickable(role = Role.Button) { menu = true }
                        .semantics { contentDescription = "설정" },
                    contentAlignment = Alignment.Center,
                ) { Icon(GearIcon, null, Modifier.size(20.dp), tint = c.muted) }
                // 설정 버튼 아래에 붙는 메뉴. 바깥을 누르거나 뒤로 가기로 닫힌다.
                DropdownMenu(menu, { menu = false }, containerColor = c.bg) {
                    MenuItem("${if (c.dark) "라이트 모드" else "다크 모드"}로 전환") { menu = false; theme.toggle() }
                    MenuItem("웹페이지 방문") { menu = false; context.startActivity(Intent(Intent.ACTION_VIEW, ORIGIN.toUri())) }
                    MenuItem("AI 연결") { menu = false; ai = true }
                    HorizontalDivider(color = c.line)
                    MenuItem("계정 설정") { menu = false; account = true }
                    MenuItem("로그아웃", enabled = !vm.busy) { menu = false; vm.logout() }
                }
            }
        }
        HorizontalDivider(color = c.line)

        Box(Modifier.weight(1f).fillMaxWidth().imePadding()) {
            tabs.SaveableStateProvider(vm.view.name) {
                when (vm.view) {
                    Screen.Timer -> TimerView(vm)
                    Screen.Log -> LogView(vm)
                    Screen.Memo -> MemoBoard(vm.memos, collapsible = true, emptyText = "항상 표시할 메모를 남겨보세요.", scroll = true)
                    Screen.Summary -> SummaryView(vm)
                }
            }
        }

        HorizontalDivider(color = c.line)
        Row(Modifier.fillMaxWidth().background(c.bg)) {
            Screen.entries.forEachIndexed { i, v ->
                val on = vm.view == v
                Box(Modifier.weight(1f).height(52.dp).clickable(role = Role.Tab) { vm.select(v) }, contentAlignment = Alignment.Center) {
                    if (i > 0) Box(Modifier.align(Alignment.CenterStart).width(1.dp).height(14.dp).background(c.line))
                    Text(v.title, color = if (on) c.fg else c.muted, fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }

    if (ai) AiDialogs(vm.api) { ai = false }
    if (account) AccountDialog(vm.api, onClose = { account = false }, onDeleted = vm::onDeleted, onAccountDeleted = vm::signedOut)
}

@Composable
private fun MenuItem(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text, color = palette().fg, fontSize = 15.sp) }, onClick = onClick, enabled = enabled)
}
