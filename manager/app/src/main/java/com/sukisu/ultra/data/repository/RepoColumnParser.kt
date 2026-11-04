package com.sukisu.ultra.data.repository

import com.sukisu.ultra.data.model.RepoColumn
import com.sukisu.ultra.data.model.RepoColumnEvidence
import com.sukisu.ultra.data.model.RepoColumnGuide
import com.sukisu.ultra.data.model.RepoColumnKeybox
import com.sukisu.ultra.data.model.RepoColumnStep
import com.sukisu.ultra.data.model.RepoColumnStepSource
import com.sukisu.ultra.data.model.RepoColumnTopic
import org.json.JSONArray
import org.json.JSONObject

/**
 * 解析索引仓的 `json/columns.json`（商店「专栏」）。
 *
 * 与 [RepoModuleParser] 一样只用 `org.json`，不引第三方库。区别在于这份文档是运营手写的：字段漏填、
 * 类型写错、某一条专题写坏了都会发生，而专栏只是商店里的可选内容——**任何一处坏数据都不该让整份
 * 专栏消失**。所以每一层都防御着读，坏掉的只跳过那一条。
 */
object RepoColumnParser {

    /** 步骤类型的规范写法；比较时忽略大小写，交给界面的一定是这里面的某一个。 */
    private val STEP_KINDS = listOf(
        "installModule",
        "installApp",
        "writeConfig",
        "writeFile",
        "writeHmaConfig",
        "runCommand",
        "verify",
        "reboot",
        "askUser",
    )

    /**
     * 未知或缺失的步骤类型归到这里：`askUser` 是唯一一种「只提示用户，什么都不动」的动作，
     * 拿它兜底不会让界面去猜一个不认识的类型该不该动设备。
     */
    private const val DEFAULT_STEP_KIND = "askUser"

    /** 风险等级的白名单。 */
    private val RISK_LEVELS = setOf("low", "medium", "high")

    /**
     * 缺失或无法识别的风险等级归到 `high`。
     *
     * 这是有意的「往重里说」：风险标签决定界面给不给红字警告与勾选确认，数据没说清时少报一档，
     * 用户就会在少一次警告的情况下继续。
     */
    private const val DEFAULT_RISK_LEVEL = "high"

    /**
     * 解析失败返回 null；单个 column/topic/step 坏掉只跳过它，不整份失败。
     *
     * @return 按 `order` 升序排好的专栏；没有 `columns` 数组、顶层不是 JSON 对象或正文不是 JSON 时
     *   返回 null（这三种都是「这份文档压根不是专栏」，页面据此整块隐藏专栏区）。
     */
    fun parse(body: String): List<RepoColumn>? {
        val root = runCatching {
            JSONObject(body.trimStart('\uFEFF').trimStart())
        }.getOrNull() ?: return null
        val columns = root.optJSONArray("columns") ?: return null
        return columns.toObjects(::parseColumn).sortedBy { it.order }
    }

    private fun parseColumn(item: JSONObject): RepoColumn? {
        val id = item.text("id")
        val title = item.text("title")
        if (id.isEmpty() || title.isEmpty()) return null
        return RepoColumn(
            id = id,
            title = title,
            subtitle = item.text("subtitle"),
            description = item.text("description"),
            accent = item.text("accent"),
            icon = item.text("icon"),
            // 没写 order 的排最后：解析结果已经排过序，这个值只是让界面自己再排一遍时结论一致。
            order = item.optInt("order", Int.MAX_VALUE),
            topics = item.optJSONArray("topics").toObjects(::parseTopic),
            guide = item.optJSONArray("guide").toObjects(::parseGuide),
        )
    }

    /**
     * 解析「怎么选」的一条说明。
     *
     * 标题为空的直接丢：正文可以缺，标题缺了这条卡在界面上就没有主语。
     */
    private fun parseGuide(item: JSONObject): RepoColumnGuide? {
        val title = item.text("title")
        if (title.isEmpty()) return null
        return RepoColumnGuide(
            tier = item.text("tier").lowercase(),
            title = title,
            body = item.text("body"),
        )
    }

    private fun parseTopic(item: JSONObject): RepoColumnTopic? {
        val id = item.text("id")
        val title = item.text("title")
        if (id.isEmpty() || title.isEmpty()) return null
        val risk = item.text("riskLevel").lowercase()
        return RepoColumnTopic(
            id = id,
            title = title,
            summary = item.text("summary"),
            riskLevel = if (risk in RISK_LEVELS) risk else DEFAULT_RISK_LEVEL,
            layers = item.optJSONArray("layers").toStringList(),
            requires = item.optJSONArray("requires").toStringList(),
            // 没有 evidence 的专题不丢：全空对象会让新鲜度自然落到 GRAY，界面照实说「没有证据」。
            evidence = item.optJSONObject("evidence")?.let(::parseEvidence) ?: emptyEvidence(),
            steps = item.optJSONArray("steps").toObjects(::parseStep),
            verifyHints = item.optJSONArray("verifyHints").toStringList(),
            // 没有 keybox 是常态（大部分专题不需要），只有显式给出 url 才算「这个专题要用密钥材料」。
            keybox = item.optJSONObject("keybox")?.let(::parseKeybox)?.takeIf { it.url.isNotEmpty() },
            tier = item.text("tier").lowercase(),
        )
    }

    private fun parseKeybox(item: JSONObject): RepoColumnKeybox = RepoColumnKeybox(
        url = item.text("url"),
        sha256 = item.text("sha256").lowercase(),
        lastVerifiedAt = item.text("lastVerifiedAt"),
        providerNote = item.text("providerNote"),
        disclaimer = item.text("disclaimer"),
    )

    private fun parseEvidence(item: JSONObject): RepoColumnEvidence = RepoColumnEvidence(
        verifiedAt = item.text("verifiedAt"),
        device = item.text("device"),
        kernel = item.text("kernel"),
        source = item.text("source"),
        note = item.text("note"),
    )

    private fun emptyEvidence(): RepoColumnEvidence = RepoColumnEvidence(
        verifiedAt = "",
        device = "",
        kernel = "",
        source = "",
        note = "",
    )

    private fun parseStep(item: JSONObject): RepoColumnStep? {
        val id = item.text("id")
        val title = item.text("title")
        if (id.isEmpty() || title.isEmpty()) return null
        val rawKind = item.text("kind")
        return RepoColumnStep(
            id = id,
            title = title,
            // 认出来的类型一律回落成规范写法（`installmodule` → `installModule`），界面只认这一套。
            kind = STEP_KINDS.firstOrNull { it.equals(rawKind, ignoreCase = true) } ?: DEFAULT_STEP_KIND,
            optional = item.optBoolean("optional", false),
            note = item.text("note"),
            source = item.optJSONObject("source")?.let(::parseStepSource) ?: emptyStepSource(),
            command = item.text("command"),
            filePath = item.text("filePath"),
            fileContent = item.rawText("fileContent"),
            checkId = item.text("checkId"),
            confirm = item.text("confirm"),
            link = item.text("link"),
            optIn = item.optBoolean("optIn", false),
        )
    }

    /**
     * 取正文类字段（可能带换行与缩进）：只去掉首尾空白，**不做 trim 内部**。
     *
     * 不能复用 [text]：它按行 trim 会让脚本里的缩进丢失；但首尾空白仍然要去，否则 JSON 里为了
     * 好看写的换行会被原样写进目标文件。
     */
    private fun JSONObject.rawText(key: String): String =
        if (isNull(key)) "" else optString(key, "").trim()

    private fun parseStepSource(item: JSONObject): RepoColumnStepSource = RepoColumnStepSource(
        kind = item.text("kind"),
        repo = item.text("repo"),
        assetPattern = item.text("assetPattern"),
        pinnedVersion = item.text("pinnedVersion"),
        sha256 = item.text("sha256"),
        moduleId = item.text("moduleId"),
        url = item.text("url"),
    )

    private fun emptyStepSource(): RepoColumnStepSource = RepoColumnStepSource(
        kind = "",
        repo = "",
        assetPattern = "",
        pinnedVersion = "",
        sha256 = "",
        moduleId = "",
        url = "",
    )

    /**
     * 取一个字符串字段并去掉首尾空白；缺失或显式 `null` 都当空串。
     *
     * 不能直接用 `optString(key, "")`：org.json 对显式 `null` 返回的是字面量 `"null"`，
     * 那会把「没填」变成一句看得见的废话。`isNull` 能把这两种情况一起挡住。
     */
    private fun JSONObject.text(key: String): String =
        if (isNull(key)) "" else optString(key, "").trim()

    /** 把数组里的对象逐条交给 [transform]；坏掉的单条只丢它自己。 */
    private fun <T> JSONArray?.toObjects(transform: (JSONObject) -> T?): List<T> {
        if (this == null) return emptyList()
        val out = ArrayList<T>(length())
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val parsed = runCatching { transform(item) }.getOrNull() ?: continue
            out.add(parsed)
        }
        return out
    }

    /**
     * 字符串数组，去掉空白项与 `null` 项。
     *
     * 也接受数字与布尔：运营在 `layers` 里写成 `12`（而不是 `"12"`）时不该被静默吞掉。
     */
    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val out = ArrayList<String>(length())
        for (index in 0 until length()) {
            val text = when (val value = opt(index)) {
                is String -> value.trim()
                is Number, is Boolean -> value.toString()
                else -> ""
            }
            if (text.isNotEmpty()) out.add(text)
        }
        return out
    }
}
