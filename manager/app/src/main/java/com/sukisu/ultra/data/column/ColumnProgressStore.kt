package com.sukisu.ultra.data.column

import android.content.Context
import com.sukisu.ultra.ksuApp
import org.json.JSONArray
import org.json.JSONObject

/**
 * 一条「这个专题最近做到哪」的记录。
 *
 * 主页的进度卡片只读这一份数据：不连 root、不发网络请求，打开主页就是一次
 * SharedPreferences 读。真实设备状态要等用户点进向导时才用自检探针去核。
 */
data class ColumnProgressEntry(
    val columnId: String,
    val topicId: String,
    val topicTitle: String,
    val lastActiveAt: Long,
    val settledSteps: Int,
    val totalSteps: Int,
    /** 专题级别的完成态：true 时不再出现在主页卡片上。 */
    val done: Boolean,
    /** 下一步该做什么的标题；全部落定时为空串。 */
    val nextStepTitle: String,
) {
    val isRunning: Boolean get() = !done && settledSteps in 1 until totalSteps

    val progressText: String get() = "$settledSteps / $totalSteps"
}

/**
 * 主页卡片与向导之间的进度索引。
 *
 * 向导自己那份 `wizard_progress_<columnId>_<topicId>` 是按步骤存的明细（步骤 id → 状态），
 * 主页没必要为了画一张卡把每个专题的明细全读一遍再自己算，所以这里单独维护一条汇总记录。
 * 两边写入时机完全一致（都在向导保存进度时），不会出现「明细更新了、汇总没更新」的窗口。
 */
object ColumnProgressStore {

    const val PREFS = "column_wizard"

    private const val KEY = "wizard_state"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 覆盖写入一条记录（同一 columnId + topicId 只留最新一条）。 */
    fun record(context: Context, entry: ColumnProgressEntry) {
        val next = readAll(context).filterNot {
            it.columnId == entry.columnId && it.topicId == entry.topicId
        } + entry
        writeAll(context, next)
    }

    /** 清掉一条（回滚后向导会调，避免主页继续显示已经回滚掉的进度）。 */
    fun clear(context: Context, columnId: String, topicId: String) {
        val next = readAll(context).filterNot { it.columnId == columnId && it.topicId == topicId }
        writeAll(context, next)
    }

    /**
     * 最近活跃且还没做完的那一条；没有就返回 null（主页不显示卡片）。
     *
     * 只认 `lastActiveAt` 在一个月内的记录：太久以前的进度基本等于没有了，
     * 继续挂在主页上只会让人困惑「这是什么」。
     */
    fun latest(context: Context, now: Long = System.currentTimeMillis()): ColumnProgressEntry? =
        readAll(context)
            .filter { it.isRunning && now - it.lastActiveAt <= STALE_AFTER_MS }
            .maxByOrNull { it.lastActiveAt }

    /** 所有「进行中」的记录条数，卡片上用来显示「还有 N 个」。 */
    fun runningCount(context: Context, now: Long = System.currentTimeMillis()): Int =
        readAll(context).count { it.isRunning && now - it.lastActiveAt <= STALE_AFTER_MS }

    private fun readAll(context: Context): List<ColumnProgressEntry> {
        val raw = runCatching { prefs(context).getString(KEY, null) }.getOrNull() ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val columnId = item.optString("columnId")
                    val topicId = item.optString("topicId")
                    if (columnId.isEmpty() || topicId.isEmpty()) continue
                    add(
                        ColumnProgressEntry(
                            columnId = columnId,
                            topicId = topicId,
                            topicTitle = item.optString("topicTitle"),
                            lastActiveAt = item.optLong("lastActiveAt"),
                            settledSteps = item.optInt("settledSteps"),
                            totalSteps = item.optInt("totalSteps"),
                            done = item.optBoolean("done"),
                            nextStepTitle = item.optString("nextStepTitle"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeAll(context: Context, entries: List<ColumnProgressEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("columnId", entry.columnId)
                    .put("topicId", entry.topicId)
                    .put("topicTitle", entry.topicTitle)
                    .put("lastActiveAt", entry.lastActiveAt)
                    .put("settledSteps", entry.settledSteps)
                    .put("totalSteps", entry.totalSteps)
                    .put("done", entry.done)
                    .put("nextStepTitle", entry.nextStepTitle),
            )
        }
        runCatching {
            prefs(context).edit().putString(KEY, array.toString()).apply()
        }
    }

    /** 主页卡片的便捷入口：直接拿应用上下文。 */
    fun latest(): ColumnProgressEntry? = latest(ksuApp)

    fun runningCount(): Int = runningCount(ksuApp)

    /** 记录超过这个时间就不再当作「进行中」。 */
    private const val STALE_AFTER_MS = 30L * 24 * 60 * 60 * 1000
}
