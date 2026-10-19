package io.github.rmia4.worktimer.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import io.github.rmia4.worktimer.WorkTimerApp
import io.github.rmia4.worktimer.data.Task
import io.github.rmia4.worktimer.data.errorText
import io.github.rmia4.worktimer.data.json
import io.github.rmia4.worktimer.util.today
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

// 알림의 일시정지/다시 시작 버튼. 서버에 보낸 뒤 최신 상태로 알림을 다시 그리고, 앱이 열려 있으면 다시 불러오게 한다.
class TaskActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as WorkTimerApp
        val action = intent.getStringExtra("action") ?: return
        val id = intent.getStringExtra("id") ?: return
        val body = buildJsonObject { put("action", action); put("id", id); put("version", intent.getIntExtra("version", 0)) }
        val pending = goAsync()
        MainScope().launch {
            try {
                val r = app.api.authed("/api/tasks", "POST", body) ?: return@launch TaskNotification.cancel(app)
                if (!r.ok) Toast.makeText(app, r.error?.takeIf { it.isNotEmpty() } ?: "저장에 실패했습니다.", Toast.LENGTH_LONG).show()
                else if (action == "resume") TaskNotification.claim(app, id)
                // 실패(다른 곳에서 바뀐 경우 포함)해도 최신 상태로 알림을 맞춘다. 진행 중 작업은 어느 날짜로 물어도 함께 온다.
                val list = app.api.get("/api/tasks?day=${today()}") ?: return@launch TaskNotification.cancel(app)
                val task = json.decodeFromJsonElement<List<Task>>(list["tasks"]!!).firstOrNull { !it.done }
                TaskNotification.update(app, task, list["now"]!!.jsonPrimitive.long - System.currentTimeMillis())
                app.changed.tryEmit(Unit)
            } catch (e: Exception) {
                Toast.makeText(app, errorText(e), Toast.LENGTH_LONG).show()
            } finally {
                pending.finish()
            }
        }
    }
}
