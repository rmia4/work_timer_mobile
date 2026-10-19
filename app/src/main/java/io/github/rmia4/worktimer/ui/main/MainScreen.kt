package io.github.rmia4.worktimer.ui.main

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rmia4.worktimer.WorkTimerApp
import io.github.rmia4.worktimer.notify.TaskNotification
import io.github.rmia4.worktimer.ui.components.GearIcon
import io.github.rmia4.worktimer.ui.components.palette
import io.github.rmia4.worktimer.ui.log.LogView
import io.github.rmia4.worktimer.ui.memo.MemoBoard
import io.github.rmia4.worktimer.ui.settings.SettingsScreen
import io.github.rmia4.worktimer.ui.summary.SummaryView
import io.github.rmia4.worktimer.ui.timer.TimerView
import kotlinx.coroutines.delay

@Composable
fun MainScreen(app: WorkTimerApp, key: String) {
    val vm = viewModel(key = key) { MainViewModel(app) }
    val c = palette()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // 탭을 오가도 각 탭의 입력 내용(rememberSaveable)을 지킨다.
    val tabs = rememberSaveableStateHolder()
    val pager = rememberPagerState(vm.view.ordinal) { Screen.entries.size }
    LaunchedEffect(pager.settledPage) { vm.select(Screen.entries[pager.settledPage]) }
    // 아래 탭을 누르거나 다른 화면에서 탭을 바꾸면(예: 일지에서 수정) 그 탭으로 넘긴다.
    LaunchedEffect(vm.view) { if (pager.settledPage != vm.view.ordinal) pager.animateScrollToPage(vm.view.ordinal) }

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

    // 알림으로 보여 줄 작업이 처음 생겼는데 알림 권한이 없으면 한 번만 묻는다(Android 13+). 이후는 설정에서 고를 때 묻는다.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.notifyTask() }
    LaunchedEffect(vm.task?.id, vm.notifyMode) {
        if (Build.VERSION.SDK_INT < 33 || app.prefs.getBoolean(NOTIFY_ASKED, false)) return@LaunchedEffect
        if (!TaskNotification.shouldShow(app, vm.task) || TaskNotification.allowed(app)) return@LaunchedEffect
        app.prefs.edit { putBoolean(NOTIFY_ASKED, true) }
        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // 설정 페이지는 메인 화면 전체를 덮는다. 위의 새로고침·메모 저장은 그대로 돈다.
    if (vm.settings) return SettingsScreen(vm) { vm.settings = false }

    Column(Modifier.fillMaxSize().background(c.page)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).background(c.bg).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(vm.view.title, Modifier.weight(1f).padding(horizontal = 8.dp), color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Box(
                Modifier
                    .size(44.dp)
                    .clickable(role = Role.Button) { vm.settings = true }
                    .semantics { contentDescription = "설정" },
                contentAlignment = Alignment.Center,
            ) { Icon(GearIcon, null, Modifier.size(20.dp), tint = c.muted) }
        }
        HorizontalDivider(color = c.line)

        // 좌우로 밀어 탭을 넘긴다. 넘김이 끝난 쪽을 고른 탭으로 삼는다.
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth().imePadding(), key = { Screen.entries[it].name }) { page ->
            tabs.SaveableStateProvider(Screen.entries[page].name) {
                when (Screen.entries[page]) {
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

}

private const val NOTIFY_ASKED = "notify_asked" // 알림 권한을 자동으로 물어봤는지
