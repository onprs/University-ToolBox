package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session

import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingJson
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import okhttp3.Cookie
import okhttp3.HttpUrl

/**
 * 持久化 Cookie 条目结构。
 */
@Serializable
data class StoredCookie(
    val name: String,
    val value: String,
    val expiresAt: Long,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean,
    val persistent: Boolean,
) {
    fun toOkHttpCookie(): Cookie {
        val builder = Cookie.Builder()
            .name(name)
            .value(value)
            .expiresAt(expiresAt)
            .path(path)
        if (hostOnly) {
            builder.hostOnlyDomain(domain)
        } else {
            builder.domain(domain)
        }
        if (secure) {
            builder.secure()
        }
        if (httpOnly) {
            builder.httpOnly()
        }
        return builder.build()
    }

    companion object {
        fun fromOkHttpCookie(cookie: Cookie): StoredCookie = StoredCookie(
            name = cookie.name,
            value = cookie.value,
            expiresAt = cookie.expiresAt,
            domain = cookie.domain,
            path = cookie.path,
            secure = cookie.secure,
            httpOnly = cookie.httpOnly,
            hostOnly = cookie.hostOnly,
            persistent = cookie.persistent,
        )
    }
}

/**
 * 按账号分区持久化 Cookie 载荷的加密存储抽象。
 */
interface AccountCookieStorage {
    suspend fun readSerializedCookies(accountId: String): String?

    suspend fun writeSerializedCookies(accountId: String, payload: String)

    suspend fun deleteCookies(accountId: String)
}

/**
 * 内存版 Cookie 载荷存储，用于阶段 A 离线/在线协议测试与本地单元测试。
 */
class InMemoryAccountCookieStorage : AccountCookieStorage {
    private val store = ConcurrentHashMap<String, String>()

    override suspend fun readSerializedCookies(accountId: String): String? = store[accountId]

    override suspend fun writeSerializedCookies(accountId: String, payload: String) {
        store[accountId] = payload
    }

    override suspend fun deleteCookies(accountId: String) {
        store.remove(accountId)
    }
}

/**
 * 多账号强隔离 Cookie 仓库：按 `accountId` 独立分桶与加锁持久化，严禁全局共享 CookieJar。
 */
@Singleton
class AccountCookieVault(
    private val storage: AccountCookieStorage,
    private val timeProvider: TimeProvider = TimeProvider.System,
) {
    @Inject
    constructor(storage: AccountCookieStorage) : this(
        storage = storage,
        timeProvider = TimeProvider.System,
    )

    private val accountMutexes = ConcurrentHashMap<String, Mutex>()
    private val cache = ConcurrentHashMap<String, LinkedHashMap<CookieKey, StoredCookie>>()

    /** 加载指定账号在目标 URL 下匹配且未过期的 Cookie 列表。 */
    suspend fun loadCookiesForRequest(accountId: String, url: HttpUrl): List<Cookie> {
        val mutex = mutexFor(accountId)
        return mutex.withLock {
            val bucket = ensureLoadedLocked(accountId)
            val nowMs = timeProvider.currentTimeMillis()
            val expiredKeys = ArrayList<CookieKey>()
            val matched = ArrayList<Cookie>()

            for ((key, stored) in bucket) {
                if (stored.expiresAt <= nowMs) {
                    expiredKeys += key
                    continue
                }
                val okCookie = stored.toOkHttpCookie()
                if (okCookie.matches(url)) {
                    matched += okCookie
                }
            }
            if (expiredKeys.isNotEmpty()) {
                expiredKeys.forEach { bucket.remove(it) }
                persistLocked(accountId, bucket)
            }
            matched
        }
    }

    /** 将响应下发的 Cookie 写入对应 `accountId` 的隔离分区。 */
    suspend fun saveFromResponse(accountId: String, cookies: List<Cookie>) {
        if (cookies.isEmpty()) {
            return
        }
        val mutex = mutexFor(accountId)
        mutex.withLock {
            val bucket = ensureLoadedLocked(accountId)
            val nowMs = timeProvider.currentTimeMillis()
            var changed = false

            for (cookie in cookies) {
                val key = CookieKey(name = cookie.name, domain = cookie.domain, path = cookie.path)
                if (cookie.expiresAt <= nowMs) {
                    if (bucket.remove(key) != null) {
                        changed = true
                    }
                } else {
                    bucket[key] = StoredCookie.fromOkHttpCookie(cookie)
                    changed = true
                }
            }
            if (changed) {
                persistLocked(accountId, bucket)
            }
        }
    }

    /** 查询指定账号当前缓存的全部 Cookie。 */
    suspend fun getAllCookies(accountId: String): List<StoredCookie> {
        val mutex = mutexFor(accountId)
        return mutex.withLock {
            ensureLoadedLocked(accountId).values.toList()
        }
    }

    /** 校验指定账号是否持有超星核心登录态 Cookie（UID/_uid, _d, vc3, uf）。 */
    suspend fun hasRequiredSessionCookies(accountId: String): Boolean {
        val names = getAllCookies(accountId)
            .filter { it.expiresAt > timeProvider.currentTimeMillis() }
            .map { it.name }
            .toSet()
        val hasUid = "UID" in names || "_uid" in names
        return hasUid && "_d" in names && "vc3" in names && "uf" in names
    }

    /** 将临时登录会话分区原子迁移到正式账号标识，并清理旧分区。 */
    suspend fun migrateAccountSession(fromAccountId: String, toAccountId: String) {
        if (fromAccountId == toAccountId) {
            return
        }
        val orderedIds = listOf(fromAccountId, toAccountId).sorted()
        mutexFor(orderedIds[0]).withLock {
            mutexFor(orderedIds[1]).withLock {
                val source = ensureLoadedLocked(fromAccountId)
                val target = LinkedHashMap<CookieKey, StoredCookie>(source)
                cache[toAccountId] = target
                persistLocked(toAccountId, target)
                cache.remove(fromAccountId)
                storage.deleteCookies(fromAccountId)
            }
        }
    }

    /** 清除指定账号的隔离会话 Cookie。 */
    suspend fun clearAccount(accountId: String) {
        mutexFor(accountId).withLock {
            cache.remove(accountId)
            storage.deleteCookies(accountId)
        }
    }

    /** 仅用于构造内部受控 Cookie 请求头（不写日志）。 */
    suspend fun formatCookieHeader(accountId: String, url: HttpUrl): String {
        val cookies = loadCookiesForRequest(accountId, url)
        return cookies.joinToString(separator = "; ") { "${it.name}=${it.value}" }
    }

    private fun mutexFor(accountId: String): Mutex = accountMutexes.computeIfAbsent(accountId) { Mutex() }

    private suspend fun ensureLoadedLocked(accountId: String): LinkedHashMap<CookieKey, StoredCookie> {
        cache[accountId]?.let { return it }
        val serialized = storage.readSerializedCookies(accountId)
        val map = LinkedHashMap<CookieKey, StoredCookie>()
        if (!serialized.isNullOrBlank()) {
            val decoded = runCatching {
                ChaoxingJson.codec.decodeFromString<List<StoredCookie>>(serialized)
            }.getOrDefault(emptyList())
            decoded.forEach { cookie ->
                map[CookieKey(cookie.name, cookie.domain, cookie.path)] = cookie
            }
        }
        cache[accountId] = map
        return map
    }

    private suspend fun persistLocked(accountId: String, bucket: Map<CookieKey, StoredCookie>) {
        if (bucket.isEmpty()) {
            storage.deleteCookies(accountId)
        } else {
            val encoded = ChaoxingJson.codec.encodeToString(bucket.values.toList())
            storage.writeSerializedCookies(accountId, encoded)
        }
    }

    private data class CookieKey(val name: String, val domain: String, val path: String)
}
