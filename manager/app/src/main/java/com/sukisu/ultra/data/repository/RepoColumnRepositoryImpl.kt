package com.sukisu.ultra.data.repository

import com.sukisu.ultra.data.model.RepoColumn
import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.util.hasAnyNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException

/**
 * 专栏文档是运营手写的清单，正常几十 KB。给 1 MiB 留了几十倍余量：没有上限的话，一个被换掉的
 * 地址就能让 `body.string()` 把一整个大文件读进内存。
 */
internal const val MAX_COLUMNS_RESPONSE_BYTES = 1L * 1024 * 1024

/**
 * 整次专栏请求（连接 + 重定向 + 读体）的上限。全局客户端只设了连接与读的单项超时，一个每十几秒
 * 吐一个字节的地址永远碰不到它们；只有整次调用的封顶拦得住。
 */
internal const val COLUMNS_CALL_TIMEOUT_SECONDS = 15L

/**
 * 从官方索引仓拉专栏。
 *
 * 只读：不发任何写请求、不落盘、不执行文档里的步骤。P0 阶段专栏纯粹是展示内容，装不装由用户
 * 自己在模块页里一步步操作。
 */
class RepoColumnRepositoryImpl(
    /** 索引地址；留成参数是为了让测试能把这一层指向本地假数据。 */
    private val url: String = OFFICIAL_COLUMNS_URL,
) : RepoColumnRepository {

    /**
     * 懒初始化：不在构造时碰 [ksuApp]（ViewModel 可能在某些预览/测试环境里先被构造）。
     * `newBuilder` 复制的是共享的连接池与线程池，不会额外建连接资源。
     */
    private val client: OkHttpClient by lazy {
        ksuApp.okhttpClient.newBuilder()
            .callTimeout(Duration.ofSeconds(COLUMNS_CALL_TIMEOUT_SECONDS))
            .build()
    }

    override suspend fun fetchColumns(): Result<List<RepoColumn>> = withContext(Dispatchers.IO) {
        try {
            // 用软检查（hasAnyNetwork）而不是要求 NET_CAPABILITY_VALIDATED 的硬检查：有些 ROM
            // 从不把网络标记成已验证，硬检查会把明明能上网的机器判成离线。
            if (!hasAnyNetwork(ksuApp)) throw IOException("no network available")

            val request = Request.Builder().url(url).build()
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                response.readIndexBody(MAX_COLUMNS_RESPONSE_BYTES)
            }

            // 解析不出 columns 数组说明这份文档不是专栏（地址被换掉、CDN 返回了别的内容）。
            val columns = RepoColumnParser.parse(body)
                ?: throw IOException("columns document has no column list")
            Result.success(columns)
        } catch (e: CancellationException) {
            // 取消不是失败：吞掉它会把这层变成一个「正常的失败结果」，上层就再也取消不掉了。
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
