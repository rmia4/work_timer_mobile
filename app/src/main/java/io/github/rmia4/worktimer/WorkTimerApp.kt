package io.github.rmia4.worktimer

import androidx.core.content.edit
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import io.github.rmia4.worktimer.data.Api
import io.github.rmia4.worktimer.data.CookieStore
import io.github.rmia4.worktimer.data.SavedCookieJar
import io.github.rmia4.worktimer.notify.TaskNotification
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

enum class SignIn { Checking, SignedOut, SignedIn }

// 앱 전체에서 하나만 쓰는 객체들. 의존성 주입 도구 대신 Application에 둔다.
class WorkTimerApp : Application() {
    lateinit var prefs: SharedPreferences
        private set
    lateinit var api: Api
        private set
    val signIn = MutableStateFlow(SignIn.Checking)
    val changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1) // 알림 버튼으로 기록을 바꿨다(열린 화면이 다시 불러온다)
    val finishRequest = MutableStateFlow<String?>(null) // 알림의 종료·저장으로 앱을 열었을 때의 작업 id

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("work_timer", Context.MODE_PRIVATE)
        val saved = getSharedPreferences("cookies", Context.MODE_PRIVATE)
        val store = object : CookieStore {
            override fun load(): Set<String> = saved.getStringSet("all", null) ?: emptySet()
            override fun save(cookies: Set<String>) = saved.edit { putStringSet("all", cookies) }
        }
        val client = OkHttpClient.Builder()
            .cookieJar(SavedCookieJar(store))
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
        api = Api(client) {
            signIn.value = SignIn.SignedOut
            TaskNotification.cancel(this)
        }
    }
}
