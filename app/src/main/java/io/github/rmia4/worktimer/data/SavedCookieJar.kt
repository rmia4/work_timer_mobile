package io.github.rmia4.worktimer.data

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

// 쿠키를 저장할 곳. 앱에서는 SharedPreferences, 테스트에서는 메모리를 쓴다.
interface CookieStore {
    fun load(): Set<String>
    fun save(cookies: Set<String>)
}

// 로그인 쿠키를 앱을 다시 열어도 쓰도록 저장하는 CookieJar.
class SavedCookieJar(private val store: CookieStore) : CookieJar {
    private val cookies = mutableMapOf<String, Cookie>()

    init {
        for (line in store.load()) {
            val (domain, setCookie) = line.split('\t', limit = 2).takeIf { it.size == 2 } ?: continue
            Cookie.parse("https://$domain/".toHttpUrl(), setCookie)?.let { cookies[key(it)] = it }
        }
    }

    private fun key(c: Cookie) = "${c.name}|${c.domain}|${c.path}"

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        for (c in cookies) {
            // 만료 시각이 지난 쿠키는 지우라는 뜻이다(로그아웃).
            if (c.expiresAt <= now) this.cookies.remove(key(c)) else this.cookies[key(c)] = c
        }
        store.save(this.cookies.values.map { "${it.domain}\t$it" }.toSet())
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return cookies.values.filter { it.expiresAt > now && it.matches(url) }
    }
}
