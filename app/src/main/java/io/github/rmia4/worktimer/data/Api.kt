package io.github.rmia4.worktimer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

// Work Timer 웹앱 API를 로그인 쿠키로 부른다(크롬 확장 api.js와 같은 요청).
// 앱은 Origin을 보내지 않으므로 서버가 쓰기 요청을 허용하도록 모바일 헤더를 붙인다(웹앱 lib/auth.ts trustedOrigin).
const val ORIGIN = "https://work-timer-mauve-seven.vercel.app"

val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

class ApiResult(val ok: Boolean, val status: Int, val data: JsonObject) {
    val error: String? get() = data["error"]?.jsonPrimitive?.contentOrNull
}

class ApiException(message: String) : Exception(message)

class Api(
    private val client: OkHttpClient,
    private val origin: String = ORIGIN,
    // 세션이 끝났을 때(401) 할 일.
    private val onUnauthorized: () -> Unit,
) {
    private fun request(path: String, method: String = "GET", body: JsonElement? = null): Request {
        val builder = Request.Builder()
            .url(origin + path)
            .header("x-work-timer-client", "mobile")
            .header("Accept", "application/json")
        // OkHttp는 POST·PATCH에 본문을 요구하므로 보낼 것이 없으면 빈 본문을 붙인다.
        val requestBody = body?.toString()?.toRequestBody("application/json".toMediaType())
            ?: if (method == "POST" || method == "PATCH" || method == "PUT") ByteArray(0).toRequestBody() else null
        return builder.method(method, requestBody).build()
    }

    // 응답 본문이 JSON이 아니면 빈 객체로 본다.
    suspend fun call(path: String, method: String = "GET", body: JsonElement? = null): ApiResult = withContext(Dispatchers.IO) {
        client.newCall(request(path, method, body)).execute().use { r ->
            val data = runCatching { json.parseToJsonElement(r.body.string()) as JsonObject }.getOrElse { JsonObject(emptyMap()) }
            ApiResult(r.isSuccessful, r.code, data)
        }
    }

    // 로그인이 필요한 API. 401이면 로그인 화면으로 보내고 null을 돌려준다.
    suspend fun authed(path: String, method: String = "GET", body: JsonElement? = null): ApiResult? {
        val r = call(path, method, body)
        if (r.status != 401) return r
        onUnauthorized()
        return null
    }

    // 로그인이 필요한 GET. 실패하면 서버 오류 문구로 던지고, 401이면 null.
    suspend fun get(path: String): JsonObject? {
        val r = authed(path) ?: return null
        if (!r.ok) throw ApiException(r.error ?: "")
        return r.data
    }

    // 로그인이 필요한 파일 받기. 바이트를 그대로 돌려준다(CSV 앞의 BOM을 지키기 위해).
    suspend fun download(path: String): ByteArray? = withContext(Dispatchers.IO) {
        client.newCall(request(path)).execute().use { r ->
            if (r.code == 401) {
                onUnauthorized()
                return@withContext null
            }
            if (!r.isSuccessful) {
                val data = runCatching { json.parseToJsonElement(r.body.string()) as JsonObject }.getOrNull()
                throw ApiException(data?.get("error")?.jsonPrimitive?.contentOrNull ?: "데이터를 내보내지 못했습니다.")
            }
            r.body.bytes()
        }
    }
}

// 오류를 화면 문구로 바꾼다. 서버 문구가 없거나 연결 오류면 fallback.
fun errorText(e: Throwable, fallback: String = "연결을 확인해 주세요."): String =
    (e as? ApiException)?.message?.takeIf { it.isNotEmpty() } ?: fallback
