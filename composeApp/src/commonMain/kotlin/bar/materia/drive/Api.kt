package bar.materia.drive

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.http.Cookie
import io.ktor.http.Url
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Same endpoints as the web apps. The Darwin engine uses NSURLSession's shared cookie store, so the
 *  `.materia.bar` session cookie set by sign-in is sent to drive.materia.bar too. */
object Endpoints {
    const val ACCOUNTS = "https://materia.bar"
    const val DRIVE = "https://drive.materia.bar"
}

@Serializable data class Account(val id: String, val username: String, val displayName: String, val email: String)
@Serializable data class Me(val usedBytes: Long, val quotaBytes: Long, val avatar: String? = null)
@Serializable data class DriveFile(
    val id: String,
    val name: String,
    val isFolder: Boolean,
    val mime: String? = null,
    val size: Long = 0,
    val color: String? = null,
    val starred: Boolean = false,
    val hasThumb: Boolean = false,
    val encrypted: Boolean = false,
    val role: String? = null,
) {
    val isImage get() = mime?.startsWith("image/") == true
    val isVideo get() = mime?.startsWith("video/") == true
    val thumbUrl get() = "${Endpoints.DRIVE}/api/files/$id/thumb"
    val contentUrl get() = "${Endpoints.DRIVE}/api/files/$id/content"
    val canManage get() = role == null || role == "owner"
}
@Serializable private data class FileList(val items: List<DriveFile>)
@Serializable private data class Session(val user: Account)
@Serializable private data class MeResponse(val user: Me)

class ApiException(message: String) : Exception(message)

class Api {
    val http = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        // Ktor's Darwin engine ignores NSURLSession cookies, so keep the session cookie ourselves.
        install(HttpCookies) { storage = SavedCookies }
        expectSuccess = false
    }

    private suspend fun check(r: HttpResponse): HttpResponse {
        if (r.status.isSuccess()) return r
        val msg = runCatching { r.body<JsonObject>()["error"]?.jsonPrimitive?.content }.getOrNull()
        throw ApiException(msg ?: "Request failed (${r.status.value})")
    }

    suspend fun session(): Account? {
        val r = http.get("${Endpoints.ACCOUNTS}/api/session")
        return if (r.status.isSuccess()) r.body<Session>().user else null
    }

    suspend fun signIn(login: String, password: String): Account {
        check(http.post("${Endpoints.ACCOUNTS}/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("login" to login, "password" to password))
        })
        return session() ?: throw ApiException("Couldn't start a session")
    }

    suspend fun signOut() { runCatching { http.post("${Endpoints.ACCOUNTS}/api/auth/logout") }; saveSession(null) }

    suspend fun me(): Me = check(http.get("${Endpoints.DRIVE}/api/me")).body<MeResponse>().user

    suspend fun list(view: String = "drive", parent: String? = null): List<DriveFile> =
        check(http.get("${Endpoints.DRIVE}/api/files") {
            parameter("view", view)
            parameter("sort", if (view == "photos") "taken" else "name")
            parameter("dir", if (view == "photos") "desc" else "asc")
            parent?.let { parameter("parent", it) }
        }).body<FileList>().items

    suspend fun createFolder(name: String, parent: String?) {
        check(http.post("${Endpoints.DRIVE}/api/files") {
            contentType(ContentType.Application.Json)
            setBody(buildMap { put("name", name); parent?.let { put("parentId", it) } })
        })
    }

    suspend fun batch(action: String, ids: List<String>) {
        check(http.post("${Endpoints.DRIVE}/api/files/batch") {
            contentType(ContentType.Application.Json)
            setBody(BatchBody(action, ids))
        })
    }
    @Serializable private data class BatchBody(val action: String, val ids: List<String>)
}

fun Long.bytes(): String {
    if (this < 1024) return "$this B"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = this / 1024.0
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return "${(v * 10).toLong() / 10.0} ${units[i]}"
}

expect fun loadSession(): String?
expect fun saveSession(token: String?)

/** In-memory cookie jar that also persists `md_session`, so sign-in survives app restarts. */
private object SavedCookies : CookiesStorage {
    private val jar = AcceptAllCookiesStorage()
    private var restored = false
    private suspend fun restore() {
        if (restored) return
        restored = true
        loadSession()?.let { jar.addCookie(Url(Endpoints.ACCOUNTS), Cookie("md_session", it, domain = "materia.bar", path = "/", secure = true, httpOnly = true)) }
    }
    override suspend fun get(requestUrl: Url): List<Cookie> { restore(); return jar.get(requestUrl) }
    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        restore(); jar.addCookie(requestUrl, cookie)
        if (cookie.name == "md_session") saveSession(cookie.value.ifEmpty { null })
    }
    override fun close() = jar.close()
}
