package io.github.rmia4.worktimer.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import io.github.rmia4.worktimer.MainActivity
import io.github.rmia4.worktimer.R
import io.github.rmia4.worktimer.WorkTimerApp
import io.github.rmia4.worktimer.data.Task
import io.github.rmia4.worktimer.util.duration
import io.github.rmia4.worktimer.util.elapsedOf

// 설정의 알림 범위: 알림 없음, 이 앱에서 시작한 작업만, 어디서 시작했든 모두.
enum class NotifyMode { Off, Mine, All }

// 기록 중인 작업을 알림창에 고정해 보여 준다(작업명·경과 시간·일시정지/다시 시작·종료·저장).
// 앱에만 있는 기능이라 대응하는 확장 파일은 없다. 버튼 문구는 타이머 화면(TimerView)과 같다.
object TaskNotification {
    const val FINISH = "finish" // 종료·저장 버튼으로 앱을 열 때 넘기는 작업 id
    private const val CHANNEL = "task"
    private const val ID = 1
    private const val MODE_KEY = "notify"
    private const val MINE_KEY = "notify_mine" // 이 앱에서 마지막으로 시작·재개한 작업 id

    fun mode(app: WorkTimerApp) = NotifyMode.entries.firstOrNull { it.name == app.prefs.getString(MODE_KEY, null) } ?: NotifyMode.Mine
    fun setMode(app: WorkTimerApp, mode: NotifyMode) = app.prefs.edit { putString(MODE_KEY, mode.name) }
    fun claim(app: WorkTimerApp, id: String) = app.prefs.edit { putString(MINE_KEY, id) }

    // Android 12 이하는 권한 없이 알림을 띄울 수 있다(사용자가 앱 알림을 껐는지만 본다).
    fun allowed(context: Context) =
        if (Build.VERSION.SDK_INT < 33) NotificationManagerCompat.from(context).areNotificationsEnabled()
        else ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun shouldShow(app: WorkTimerApp, task: Task?) = task != null && when (mode(app)) {
        NotifyMode.Off -> false
        NotifyMode.Mine -> task.id == app.prefs.getString(MINE_KEY, null)
        NotifyMode.All -> true
    }

    // 진행 중 작업과 설정에 맞게 알림을 띄우거나 지운다. offset은 서버 시각 - 기기 시각.
    fun update(app: WorkTimerApp, task: Task?, offset: Long) {
        if (task == null || !shouldShow(app, task)) return cancel(app)
        if (!allowed(app)) return
        val manager = NotificationManagerCompat.from(app)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW).setName("기록 중인 작업").build()
        )
        val running = task.status == "running"
        val elapsed = elapsedOf(task, System.currentTimeMillis() + offset)
        val target = if (task.target > 0) " · 목표 ${duration(task.target)}" else ""
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(app, 0, Intent(app, MainActivity::class.java), flags)
        val finish = PendingIntent.getActivity(app, 1, Intent(app, MainActivity::class.java).putExtra(FINISH, task.id), flags)
        val toggle = PendingIntent.getBroadcast(
            app, 2,
            Intent(app, TaskActionReceiver::class.java)
                .putExtra("action", if (running) "pause" else "resume")
                .putExtra("id", task.id)
                .putExtra("version", task.version),
            flags,
        )
        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(if (running) "측정 중$target" else "일시정지 · ${duration(elapsed)}$target")
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            // 측정 중이면 시스템이 시작 시각부터 경과 시간을 1초마다 그린다.
            .setShowWhen(running)
            .setUsesChronometer(running)
            .setWhen(System.currentTimeMillis() - elapsed)
            .setContentIntent(open)
            .addAction(0, if (running) "일시정지" else "다시 시작", toggle)
            .addAction(0, "종료·저장", finish)
            .build()
        try {
            manager.notify(ID, notification)
        } catch (_: SecurityException) {
            // 확인한 뒤 권한이 꺼졌으면 띄우지 않는다.
        }
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(ID)
}
