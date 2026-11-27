package com.sukisu.ultra.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import com.sukisu.ultra.data.model.Module
import com.sukisu.ultra.data.model.ModuleUpdateInfo
import com.sukisu.ultra.data.proxy.GithubProxyRouter
import com.sukisu.ultra.data.proxy.ProxyMode
import com.sukisu.ultra.data.proxy.ProxyRuntime
import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.util.isNetworkAvailable
import com.sukisu.ultra.ui.util.listModules
import com.sukisu.ultra.ui.util.module.sanitizeVersionString
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resumeWithException

class ModuleRepositoryImpl : ModuleRepository {

    companion object {
        private const val TAG = "ModuleRepository"
    }

    override suspend fun getModules(): Result<List<Module>> = withContext(Dispatchers.IO) {
        runCatching {
            val result = listModules()
            val array = JSONArray(result)
            (0 until array.length())
                .asSequence()
                .map { array.getJSONObject(it) }
                .map { obj ->
                    Module(
                        id = obj.getString("id"),
                        name = obj.optString("name"),
                        author = obj.optString("author", "Unknown"),
                        version = obj.optString("version", "Unknown"),
                        versionCode = obj.optLong("versionCode", 0L),
                        description = obj.optString("description"),
                        enabled = obj.getBoolean("enabled"),
                        update = obj.optBoolean("update"),
                        remove = obj.getBoolean("remove"),
                        updateJson = obj.optString("updateJson"),
                        hasWebUi = obj.optBoolean("web"),
                        hasActionScript = obj.optBoolean("action"),
                        metamodule = (obj.optInt("metamodule") != 0) || obj.optBoolean("metamodule"),
                        zygisk = (obj.optInt("zygisk") != 0) || obj.optBoolean("zygisk"),
                        actionIconPath = obj.optString("actionIcon").takeIf { it.isNotBlank() },
                        webUiIconPath = obj.optString("webuiIcon").takeIf { it.isNotBlank() },
                        preinit = obj.optString("preinit"),
                        bannerPath = obj.optString("banner").takeIf { it.isNotBlank() }
                    )
                }.toList()
        }
    }

    override suspend fun checkUpdate(module: Module): Result<ModuleUpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isNetworkAvailable(ksuApp)) {
                return@runCatching ModuleUpdateInfo.Empty
            }
            if (module.updateJson.isEmpty() || module.remove || module.update || !module.enabled) {
                return@runCatching ModuleUpdateInfo.Empty
            }

            val url = module.updateJson
            val result = fetchModuleUpdateBody(url)

            if (result.isEmpty()) {
                return@runCatching ModuleUpdateInfo.Empty
            }

            val updateJson = JSONObject(result)
            var version = updateJson.optString("version", "")
            version = sanitizeVersionString(version)
            val versionCode = updateJson.optLong("versionCode", 0L)
            val zipUrl = updateJson.optString("zipUrl", "")
            val changelog = updateJson.optString("changelog", "")
            // updateJson 允许声明 sha256 / size：声明了才做完整性校验。
            // 是否走 GitHub 加速只取决于 zipUrl 是否为可加速的 GitHub 地址，与有没有 sha256 无关。
            val sha256 = updateJson.optString("sha256", "")
            val size = updateJson.optLong("size", 0L)

            if (versionCode <= module.versionCode || zipUrl.isEmpty()) {
                ModuleUpdateInfo.Empty
            } else {
                ModuleUpdateInfo(zipUrl, version, changelog, versionCode, sha256, size)
            }
        }.onFailure { if (it is CancellationException) throw it }
    }

    /**
     * 拉取模块更新描述。GitHub 地址按当前加速设置展开候选（与下载同一条路由），
     * 其它地址仍然只直连；候选都拿不到内容时抛最后一次网络错误。
     */
    private suspend fun fetchModuleUpdateBody(url: String): String {
        val proxies = try {
            ProxyRuntime.list.load()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        val mode = try {
            ProxyRuntime.prefs.mode()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ProxyMode.AUTO
        }
        val pinnedId = try {
            ProxyRuntime.prefs.pinnedId()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        val lastGoodId = try {
            ProxyRuntime.prefs.lastGoodId()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        val candidates = GithubProxyRouter.candidates(url, mode, proxies, pinnedId, lastGoodId)
        var lastFailure: Exception? = null
        for (candidate in candidates) {
            try {
                val body = ksuApp.okhttpClient.newCall(
                    Request.Builder().url(candidate).build()
                ).awaitModuleUpdateBody()
                val parsed = runCatching { JSONObject(body) }.getOrNull()
                if (body.isNotEmpty() && parsed != null &&
                    (parsed.has("version") || parsed.has("versionCode") || parsed.has("zipUrl"))
                ) {
                    return body
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastFailure = e
            }
        }
        lastFailure?.let { throw it }
        return ""
    }
}

internal suspend fun Call.awaitModuleUpdateBody(): String = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            // Read before resuming so cancellation also cancels an in-progress body read.
            val result = runCatching {
                response.use {
                    if (continuation.isActive && it.isSuccessful) it.body.string() else ""
                }
            }
            continuation.resumeWith(result)
        }
    })
}
