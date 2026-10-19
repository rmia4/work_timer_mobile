package io.github.rmia4.worktimer

import androidx.core.content.edit
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import io.github.rmia4.worktimer.data.Api
import io.github.rmia4.worktimer.data.CookieStore
import io.github.rmia4.worktimer.data.SavedCookieJar
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
        api = Api(client) { signIn.value = SignIn.SignedOut }
    }
}
