package io.github.rmia4.worktimer.ui.main

import androidx.core.content.edit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rmia4.worktimer.WorkTimerApp
import io.github.rmia4.worktimer.data.CalendarDay
import io.github.rmia4.worktimer.data.Task
import io.github.rmia4.worktimer.data.errorText
import io.github.rmia4.worktimer.data.json
import io.github.rmia4.worktimer.ui.memo.MemoBoardState
import io.github.rmia4.worktimer.util.today
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

enum class Screen(val title: String) { Timer("타이머"), Log("일지"), Memo("메모"), Summary("요약") }

// 달마다 날짜별 작업 수·시간. now는 불러온 서버 시각(측정 중인 작업 시간을 더하는 데 쓴다).
class Month(val days: Map<String, CalendarDay>, val now: Long)

// 크롬 확장 popup.js의 상태와 동작이다. 화면을 오가도 기록·입력 내용은 여기 남는다.
class MainViewModel(private val app: WorkTimerApp) : ViewModel() {
    val api = app.api

    var view by mutableStateOf(Screen.entries.firstOrNull { it.name == app.prefs.getString(VIEW_KEY, null) } ?: Screen.Timer)
        private set
    var settings by mutableStateOf(false) // 설정 페이지를 열었는지
    var day by mutableStateOf(today()) // 일지에서 보는 날짜
        private set
    var loading by mutableStateOf(true) // 보고 있는 날짜의 기록을 아직 못 받았는지
        private set
    var tasks by mutableStateOf<List<Task>>(emptyList())
        private set
    var offset by mutableLongStateOf(0) // 서버 시각 - 기기 시각
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
    var message by mutableStateOf("")
    var edit by mutableStateOf<Task?>(null) // 수정 중인 기록
        private set
    var formKey by mutableIntStateOf(0) // 바꾸면 작업 폼을 비운다
        private set

    // 작업 캘린더 캐시("YYYY-MM" → 그 달 기록). 저장하면 그 달을 지워 다시 불러온다.
    val months = mutableStateMapOf<String, Month>()

    // 메모장과 일일 메모. 데이터를 지우면 새로 만든다.
    var memos by mutableStateOf(MemoBoardState(api, viewModelScope, "/api/memos", null))
        private set
    private var daily: MemoBoardState? = null

    // 고른 날짜의 일일 메모. 날짜가 바뀌면 이전 날짜의 입력을 저장하고 새로 불러온다.
    fun dailyMemos(day: String): MemoBoardState {
        daily?.takeIf { it.day == day }?.let { return it }
        daily?.flush()
        return MemoBoardState(api, viewModelScope, "/api/daily-memos", day).also { daily = it }
    }

    val task: Task? get() = tasks.firstOrNull { !it.done }
    fun now() = System.currentTimeMillis() + offset

    private var request = 0 // 늦게 온 이전 조회 응답을 버리기 위한 번호

    fun select(v: Screen) {
        view = v
        app.prefs.edit { putString(VIEW_KEY, v.name) }
    }

    fun load() {
        val seq = ++request
        viewModelScope.launch {
            try {
                val r = api.authed("/api/tasks?day=$day") ?: return@launch
                if (seq != request) return@launch
                if (!r.ok) throw io.github.rmia4.worktimer.data.ApiException(r.error ?: "")
                val list = json.decodeFromJsonElement<List<Task>>(r.data["tasks"]!!)
                if (list != tasks) tasks = list
                offset = r.data["now"]!!.jsonPrimitive.long - System.currentTimeMillis()
                loading = false
                error = ""
            } catch (e: Exception) {
                if (seq == request) error = errorText(e)
            }
        }
    }

    // 웹앱 use-workspace.ts의 act와 같다. 409면 최신 상태를 다시 불러온다.
    // 성공하면 null, 실패하면 오류 문구(이미 처리 중이었거나 로그아웃이면 빈 문구)를 돌려준다.
    suspend fun act(body: JsonObject): String? {
        if (busy) return ""
        busy = true
        error = ""
        message = ""
        try {
            val r = api.authed("/api/tasks", "POST", body) ?: return ""
            if (!r.ok) {
                if (r.status == 409) load()
                return r.error?.takeIf { it.isNotEmpty() } ?: "저장에 실패했습니다."
            }
            load()
            months.remove(day.take(7))
            body["day"]?.jsonPrimitive?.contentOrNull?.let { months.remove(it.take(7)) }
            message = "저장되었습니다."
            return null
        } catch (e: Exception) {
            return errorText(e, "저장에 실패했습니다.")
        } finally {
            busy = false
        }
    }

    // 화면 아래에 오류를 보여 주는 act.
    fun actShow(body: JsonObject, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val failed = act(body)
            if (!failed.isNullOrEmpty()) error = failed
            if (failed == null) onSuccess()
        }
    }

    fun clearForm() {
        edit = null
        formKey++
    }

    // 웹앱 chooseDay(): 다른 날짜를 고르면 그 날짜 기록을 새로 불러온다.
    fun chooseDay(value: String) {
        if (value == day) return
        request++
        day = value
        loading = true
        tasks = emptyList()
        if (edit != null) clearForm()
        message = ""
        load()
    }

    // 웹앱 startEdit(): 완료 기록을 타이머 화면의 폼에 채운다.
    fun startEdit(t: Task) {
        edit = t
        formKey++
        message = ""
        select(Screen.Timer)
    }

    // 슬라이딩 만료: 앞으로 돌아올 때마다 세션을 7일 뒤로 늘린다(남은 기간이 절반 이하일 때만 서버가 늘린다).
    fun renewSession() {
        viewModelScope.launch { runCatching { api.call("/api/auth", "PATCH") } }
    }

    fun logout() {
        error = ""
        viewModelScope.launch {
            try {
                if (!api.call("/api/auth", "DELETE").ok) error = "로그아웃하지 못했습니다."
                else signedOut()
            } catch (e: Exception) {
                error = errorText(e)
            }
        }
    }

    fun signedOut() {
        app.signIn.value = io.github.rmia4.worktimer.SignIn.SignedOut
    }

    // 계정 설정에서 데이터를 지웠으면 화면에서도 비운다.
    fun onDeleted(kind: String) {
        if (kind == "tasks") {
            months.clear()
            if (edit != null) clearForm()
            load()
        } else {
            memos = MemoBoardState(api, viewModelScope, "/api/memos", null)
            daily = null
        }
    }

    // 앱이 뒤로 갈 때 저장을 기다리는 메모를 바로 저장한다.
    fun flushMemos() {
        memos.flush()
        daily?.flush()
    }

    private companion object {
        const val VIEW_KEY = "view" // 마지막으로 연 화면(앱을 다시 열면 그대로 보여 준다)
    }
}
