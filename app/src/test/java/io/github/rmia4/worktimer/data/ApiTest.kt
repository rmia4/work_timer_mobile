package io.github.rmia4.worktimer.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiTest {
    private val server = MockWebServer()
    private val saved = mutableSetOf<String>()
    private val store = object : CookieStore {
        override fun load() = saved.toSet()
        override fun save(cookies: Set<String>) {
            saved.clear()
            saved.addAll(cookies)
        }
    }
    private var unauthorized = 0

    private fun api() = Api(
        OkHttpClient.Builder().cookieJar(SavedCookieJar(store)).build(),
        server.url("").toString().trimEnd('/'),
    ) { unauthorized++ }

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    @Test
    fun sendsMobileHeaderAndJsonBody() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body("""{"ok":true}""").build())
        val r = api().call("/api/auth", "POST", buildJsonObject { put("username", "kim") })
        val request = server.takeRequest()
        assertEquals("mobile", request.headers["x-work-timer-client"])
        assertEquals("""{"username":"kim"}""", request.body?.utf8())
        assertTrue(r.ok)
    }

    @Test
    fun keepsLoginCookieAcrossRestart() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body("{}").addHeader("Set-Cookie", "session=abc; Path=/; Max-Age=604800").build())
        server.enqueue(MockResponse.Builder().code(200).body("{}").build())
        api().call("/api/auth", "POST")
        // 앱을 다시 연 것처럼 새 Api(저장소는 같음)로 요청한다.
        api().call("/api/tasks")
        server.takeRequest()
        assertEquals("session=abc", server.takeRequest().headers["Cookie"])
    }

    @Test
    fun logoutCookieRemovesSavedCookie() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body("{}").addHeader("Set-Cookie", "session=abc; Path=/; Max-Age=604800").build())
        server.enqueue(MockResponse.Builder().code(200).body("{}").addHeader("Set-Cookie", "session=; Path=/; Max-Age=0").build())
        val api = api()
        api.call("/api/auth", "POST")
        api.call("/api/auth", "DELETE")
        assertTrue(saved.isEmpty())
    }

    @Test
    fun patchWithoutBodyIsSent() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body("{}").build())
        assertTrue(api().call("/api/auth", "PATCH").ok)
        assertEquals("PATCH", server.takeRequest().method)
    }

    @Test
    fun unauthorizedCallsHandlerAndReturnsNull() = runBlocking {
        server.enqueue(MockResponse.Builder().code(401).body("""{"error":"로그인이 필요합니다."}""").build())
        assertNull(api().authed("/api/tasks"))
        assertEquals(1, unauthorized)
    }

    @Test
    fun serverErrorMessageIsShown() = runBlocking {
        server.enqueue(MockResponse.Builder().code(409).body("""{"error":"다른 기기에서 바뀌었습니다."}""").build())
        val e = runCatching { api().get("/api/memos") }.exceptionOrNull()!!
        assertEquals("다른 기기에서 바뀌었습니다.", errorText(e))
    }
}
