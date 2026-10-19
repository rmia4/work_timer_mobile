package io.github.rmia4.worktimer.ui.memo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rmia4.worktimer.data.Api
import io.github.rmia4.worktimer.data.Memo
import io.github.rmia4.worktimer.data.errorText
import io.github.rmia4.worktimer.data.json
import io.github.rmia4.worktimer.ui.components.Gap
import io.github.rmia4.worktimer.ui.components.Hint
import io.github.rmia4.worktimer.ui.components.Variant
import io.github.rmia4.worktimer.ui.components.WtButton
import io.github.rmia4.worktimer.ui.components.WtDialog
import io.github.rmia4.worktimer.ui.components.palette
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put

// 크롬 확장 memo-board.js(웹앱 memo-board.tsx)의 메모 보드. 입력은 0.7초 뒤 자동 저장하고,
// 다른 기기에서 먼저 고쳤으면(409) 자동 저장을 멈추고 어느 쪽을 남길지 고르게 한다.
private const val SAVE_DELAY = 700L

// 로그아웃(401)이면 로그인 화면으로 가므로 signedOut 오류는 화면에 띄우지 않는다.
class MemoFailure(message: String, val signedOut: Boolean = false, val memo: Memo? = null) : Exception(message)

data class MemoValues(val title: String, val body: String, val collapsed: Boolean)

private fun Memo.values() = MemoValues(title, body, collapsed ?: false)

// endpoint: /api/memos(메모장) 또는 /api/daily-memos(일일 메모, day를 준다).
class MemoBoardState(private val api: Api, val scope: CoroutineScope, private val endpoint: String, val day: String?) {
    var cards by mutableStateOf<List<MemoCardState>?>(null) // null: 불러오는 중
        private set
    var error by mutableStateOf("")
    var moving by mutableStateOf(false)
        private set
    var focusId by mutableStateOf<String?>(null)
        private set

    init {
        reload()
    }

    suspend fun request(body: JsonObjectBuilder.() -> Unit): JsonObject {
        val payload = buildJsonObject {
            body()
            if (day != null) put("day", day)
        }
        val r = runCatching { api.authed(endpoint, "POST", payload) }.getOrElse { throw MemoFailure(errorText(it)) }
            ?: throw MemoFailure("", signedOut = true)
        if (!r.ok) {
            val memo = if (r.status == 409) r.data["memo"]?.let { json.decodeFromJsonElement<Memo>(it) } else null
            throw MemoFailure(r.error ?: "메모를 저장하지 못했습니다.", memo = memo)
        }
        return r.data
    }

    fun reload() {
        cards = null
        error = ""
        scope.launch {
            try {
                val data = api.get(if (day != null) "$endpoint?day=$day" else endpoint) ?: return@launch
                cards = json.decodeFromJsonElement<List<Memo>>(data["memos"]!!).map { MemoCardState(it, this@MemoBoardState) }
            } catch (e: Exception) {
                cards = emptyList()
                error = errorText(e, "메모를 불러오지 못했습니다.")
            }
        }
    }

    fun create() {
        error = ""
        scope.launch {
            try {
                val data = request { put("action", "create") }
                val memo = data["memo"]?.let { json.decodeFromJsonElement<Memo>(it) } ?: return@launch
                cards = (cards ?: emptyList()) + MemoCardState(memo, this@MemoBoardState)
                focusId = memo.id
            } catch (e: MemoFailure) {
                if (!e.signedOut) error = e.message ?: ""
            }
        }
    }

    fun move(index: Int, direction: Int) {
        val list = cards ?: return
        if (moving) return
        val swapIndex = index + direction
        if (swapIndex !in list.indices) return
        val card = list[index]
        val swap = list[swapIndex]
        cards = list.toMutableList().also { it[index] = swap; it[swapIndex] = card }
        moving = true
        error = ""
        scope.launch {
            try {
                request { put("action", "reorder"); put("id", card.id); put("swapId", swap.id) }
            } catch (e: MemoFailure) {
                // 실패하면 서버 순서로 다시 불러온 뒤 오류를 보여 준다.
                if (!e.signedOut) {
                    reload()
                    error = e.message ?: ""
                }
            }
            moving = false
        }
    }

    fun removed(card: MemoCardState) {
        cards = cards?.filter { it !== card }
    }

    // 저장을 기다리는 입력을 바로 저장한다(앱이 뒤로 갈 때, 날짜를 바꿀 때).
    fun flush() = cards?.forEach { it.flush() }
}

class MemoCardState(memo: Memo, private val board: MemoBoardState) {
    val id = memo.id
    var values by mutableStateOf(memo.values())
        private set
    var state by mutableStateOf("saved") // saved · saving · error
        private set
    var error by mutableStateOf("")
        private set
    var conflict by mutableStateOf<Memo?>(null)
        private set
    private var persisted = memo.values()
    private var version = memo.version
    private var timer: Job? = null
    private var saving = false
    private var queued = false

    private fun fail(e: MemoFailure) {
        if (e.signedOut) return
        if (e.memo != null) conflict = e.memo
        state = "error"
        error = e.message?.takeIf { it.isNotEmpty() } ?: "메모를 저장하지 못했습니다."
    }

    private fun ok() {
        state = "saved"
        error = ""
    }

    fun change(next: MemoValues) {
        values = next
        timer?.cancel()
        timer = board.scope.launch {
            delay(SAVE_DELAY)
            timer = null
            save()
        }
    }

    fun save() {
        if (conflict != null) return
        if (saving) {
            queued = true
            return
        }
        val snapshot = values
        if (snapshot == persisted) return
        saving = true
        queued = false
        state = "saving"
        board.scope.launch {
            try {
                val data = board.request {
                    put("action", "update"); put("id", id); put("version", version)
                    put("title", snapshot.title); put("body", snapshot.body); put("collapsed", snapshot.collapsed)
                }
                data["memo"]?.let {
                    version = json.decodeFromJsonElement<Memo>(it).version
                    persisted = snapshot
                }
                ok()
            } catch (e: MemoFailure) {
                fail(e)
            } finally {
                saving = false
                if (queued && conflict == null && values != snapshot) save()
            }
        }
    }

    fun flush() {
        if (timer == null) return
        timer?.cancel()
        timer = null
        save()
    }

    fun loadServerVersion() {
        val server = conflict ?: return
        values = server.values()
        version = server.version
        persisted = server.values()
        conflict = null
        ok()
    }

    fun keepMine() {
        val server = conflict ?: return
        version = server.version
        persisted = server.values()
        conflict = null
        ok()
        save()
    }

    fun toggleCollapsed() {
        values = values.copy(collapsed = !values.collapsed)
        timer?.cancel()
        timer = null
        save()
    }

    fun remove(confirm: suspend () -> Boolean) {
        board.scope.launch {
            if ((values.title.isNotBlank() || values.body.isNotBlank()) && !confirm()) return@launch
            timer?.cancel()
            timer = null
            try {
                board.request { put("action", "delete"); put("id", id); put("version", version) }
                board.removed(this@MemoCardState)
            } catch (e: MemoFailure) {
                fail(e)
            }
        }
    }
}

@Composable
fun MemoBoard(board: MemoBoardState, collapsible: Boolean, emptyText: String, scroll: Boolean) {
    val c = palette()
    // 웹앱의 window.confirm 대신 앱 안 대화상자로 묻는다(뒤로 가기·취소면 false).
    var confirm by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val askRemove: suspend () -> Boolean = { CompletableDeferred<Boolean>().also { confirm = it }.await() }
    fun answer(ok: Boolean) {
        confirm?.complete(ok)
        confirm = null
    }

    val modifier = if (scroll) Modifier.verticalScroll(rememberScrollState()).padding(16.dp) else Modifier
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (board.error.isNotEmpty()) {
            Text(board.error, color = c.danger, fontSize = 13.sp)
            WtButton("다시 불러오기", board::reload, variant = Variant.Outline, small = true)
        }
        val cards = board.cards
        when {
            cards == null -> Hint("메모를 불러오는 중입니다.")
            cards.isEmpty() -> Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Hint(emptyText)
                WtButton("+ 첫 메모 만들기", board::create, Modifier.padding(top = 8.dp), Variant.Outline, small = true)
            }
            else -> {
                cards.forEachIndexed { i, card ->
                    androidx.compose.runtime.key(card) {
                        MemoCard(
                            card,
                            collapsible,
                            canUp = !board.moving && i > 0,
                            canDown = !board.moving && i < cards.size - 1,
                            autoFocus = card.id == board.focusId,
                            onMove = { board.move(i, it) },
                            onRemove = { card.remove(askRemove) },
                        )
                    }
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    WtButton("+ 새 메모", board::create, variant = Variant.Outline, small = true)
                }
            }
        }
    }
    if (confirm != null) {
        WtDialog({ answer(false) }, "이 메모를 삭제하시겠습니까?") {
            Gap {
                WtButton("취소", { answer(false) }, Modifier.weight(1f), Variant.Outline)
                WtButton("삭제", { answer(true) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MemoCard(card: MemoCardState, collapsible: Boolean, canUp: Boolean, canDown: Boolean, autoFocus: Boolean, onMove: (Int) -> Unit, onRemove: () -> Unit) {
    val c = palette()
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) focus.requestFocus() }
    val v = card.values

    @Composable
    fun Icon(text: String, label: String, enabled: Boolean = true, onClick: () -> Unit) = Box(
        Modifier
            .widthIn(min = 32.dp)
            .heightIn(min = 36.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = c.muted, fontSize = 16.sp) }

    Column(
        Modifier
            .fillMaxWidth()
            .background(c.bg, RoundedCornerShape(8.dp))
            .border(1.dp, c.line, RoundedCornerShape(8.dp))
            .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                v.title,
                { card.change(v.copy(title = it.take(200))) },
                Modifier.weight(1f).heightIn(min = 40.dp).padding(vertical = 10.dp).focusRequester(focus)
                    .onFocusChanged { if (!it.isFocused) card.flush() },
                textStyle = TextStyle(color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                singleLine = true,
                cursorBrush = SolidColor(c.accent),
                decorationBox = { inner -> Box { if (v.title.isEmpty()) Text("제목", color = c.muted, fontSize = 15.sp); inner() } },
            )
            Text(
                when (card.state) { "saving" -> "저장 중"; "error" -> "저장 실패"; else -> "저장됨" },
                Modifier.padding(end = 4.dp),
                color = c.muted,
                fontSize = 11.sp,
            )
            Icon("↑", "위로 이동", canUp) { onMove(-1) }
            Icon("↓", "아래로 이동", canDown) { onMove(1) }
            if (collapsible) Icon(if (v.collapsed) "▾" else "▴", if (v.collapsed) "메모 펼치기" else "메모 접기", onClick = card::toggleCollapsed)
            Icon("✕", "메모 삭제", onClick = onRemove)
        }
        if (!v.collapsed) {
            BasicTextField(
                v.body,
                { card.change(v.copy(body = it.take(20000))) },
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .padding(top = 4.dp)
                    .border(1.dp, c.line, RoundedCornerShape(6.dp))
                    .padding(8.dp)
                    .onFocusChanged { if (!it.isFocused) card.flush() },
                textStyle = TextStyle(color = c.fg, fontSize = 15.sp),
                cursorBrush = SolidColor(c.accent),
                decorationBox = { inner -> Box { if (v.body.isEmpty()) Text("메모를 입력하세요.", color = c.muted, fontSize = 15.sp); inner() } },
            )
        }
        if (card.error.isNotEmpty()) {
            Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(card.error, color = c.danger, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (card.conflict != null) {
                        WtButton("최신 내용 불러오기", card::loadServerVersion, variant = Variant.Outline, small = true)
                        WtButton("내 내용으로 덮어쓰기", card::keepMine, variant = Variant.Outline, small = true)
                    } else WtButton("다시 저장", card::save, variant = Variant.Outline, small = true)
                }
            }
        }
    }
}
