package com.sukisu.ultra.ai

/**
 * 关键词打分的权重表。
 *
 * 默认值就是检测器参考条目的口径：标题精确命中压过一切别名噪音，别名只作佐证且累加封顶，
 * 正文命中只用来给「用户只粘了一小段」的情况补一点分。
 */
internal data class KeywordWeights(
    /** 低于此分的条目直接丢弃：宁可少给，也不拿不相干条目凑数。 */
    val minScore: Int = 20,
    /** 标题与 query 完全一致时的最高权重，保证它排第一。 */
    val titleExact: Int = 200,
    /** 标题被 query 整条包含（用户多粘了几行转储）。 */
    val titleContain: Int = 100,
    /** 单个别名命中的权重。 */
    val alias: Int = 20,
    /** 别名累加封顶，避免别名多的条目靠数量压过标题命中的条目。 */
    val maxAlias: Int = 60,
    /** 标题与 query 都不互相包含时，按 2 字片段补分。 */
    val fragment: Int = 5,
    /** 片段补分的个数上限。 */
    val maxFragments: Int = 8,
    /** 正文里命中一个 query 词的权重。 */
    val bodyWord: Int = 2,
    /** 正文补分上限。 */
    val maxBody: Int = 10,
    /** 判定「同族标题」的最短公共前缀。 */
    val minPrefixChars: Int = 6,
    /** 同一分组最多保留几条，避免结果被同族条目占满。 */
    val maxPerGroup: Int = 2,
)

/**
 * 一套通用的关键词检索：标题 + 别名 + 正文打分，再按分数取前 N 条并压掉冗余。
 *
 * 检测器参考条目（[DetectorAnswers]）和内置助手指南（[AssistantGuide]）共用这套逻辑，
 * 只是权重和「分组/同族」的口径不同，所以这里不绑定任何具体的数据类型。
 */
internal object KeywordMatch {

    private val whitespace = Regex("\\s+")

    fun normalize(query: String): String = query.lowercase().replace(whitespace, " ").trim()

    /**
     * 给一条记录打分。[query] 必须是 [normalize] 之后的结果。
     *
     * 标题是最强信号；别名被大量不相干条目共用，只能佐证；正文命中补一点分。
     */
    fun score(
        query: String,
        title: String,
        aliases: List<String> = emptyList(),
        body: String = "",
        weights: KeywordWeights = KeywordWeights(),
    ): Int {
        var total = 0
        val normalizedTitle = title.lowercase().trim()
        if (normalizedTitle.isNotBlank()) {
            if (query == normalizedTitle) {
                total += weights.titleExact
            } else if (query.contains(normalizedTitle)) {
                total += weights.titleContain
            }
        }
        var aliasScore = 0
        for (alias in aliases) {
            val key = alias.lowercase().trim()
            if (key.length >= 2 && query.contains(key)) aliasScore += weights.alias
        }
        total += aliasScore.coerceAtMost(weights.maxAlias)
        // 标题比用户粘贴的原文长时，用 2 字片段兜一层。
        if (normalizedTitle.length >= 2 && !query.contains(normalizedTitle)) {
            var fragments = 0
            var index = 0
            while (index + 2 <= normalizedTitle.length && fragments < weights.maxFragments) {
                if (query.contains(normalizedTitle.substring(index, index + 2))) fragments++
                index += 2
            }
            total += fragments * weights.fragment
        }
        val normalizedBody = body.lowercase()
        if (normalizedBody.isNotBlank()) {
            var hits = 0
            for (word in query.split(' ', '，', '。', ',').filter { it.length >= 3 }) {
                if (normalizedBody.contains(word)) hits++
            }
            total += (hits * weights.bodyWord).coerceAtMost(weights.maxBody)
        }
        return total
    }

    /** 同一分组的多个变体只在后缀上不同（"（b）"、"(2)"、"（1/2/3）"）。 */
    fun similarTitle(first: String, second: String, weights: KeywordWeights = KeywordWeights()): Boolean {
        val a = first.lowercase().filterNot { it.isWhitespace() }
        val b = second.lowercase().filterNot { it.isWhitespace() }
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val shorter = minOf(a.length, b.length)
        val longer = maxOf(a.length, b.length)
        var prefix = 0
        while (prefix < shorter && a[prefix] == b[prefix]) prefix++
        if (prefix < weights.minPrefixChars) return false
        return shorter * 5 >= longer * 3
    }

    /**
     * 按分数从高到低挑选，并压掉两类冗余：
     * 同一分组里标题高度相似的「同族条目」只留第一条；
     * 同一分组最多留 [KeywordWeights.maxPerGroup] 条；总数不超过 [limit]。
     */
    fun <T> rank(
        query: String,
        items: List<T>,
        limit: Int,
        title: (T) -> String,
        aliases: (T) -> List<String> = { emptyList() },
        body: (T) -> String = { "" },
        group: (T) -> String = { "" },
        id: (T) -> String = { "" },
        weights: KeywordWeights = KeywordWeights(),
    ): List<T> {
        val normalized = normalize(query)
        if (normalized.isEmpty() || limit <= 0) return emptyList()
        val ranked = items
            .map { item -> item to score(normalized, title(item), aliases(item), body(item), weights) }
            .filter { it.second >= weights.minScore }
            .sortedByDescending { it.second }
            .map { it.first }
        val picked = mutableListOf<T>()
        val perGroup = mutableMapOf<String, Int>()
        for (item in ranked) {
            if (picked.size >= limit) break
            // 数据里每条都带分组；万一缺失，用 id 兜底，避免空分组互相挤占名额。
            val key = group(item).trim().ifEmpty { id(item) }
            val used = perGroup[key] ?: 0
            if (used >= weights.maxPerGroup) continue
            if (picked.any {
                    group(it).trim().equals(group(item).trim(), ignoreCase = true) &&
                        similarTitle(title(it), title(item), weights)
                }
            ) {
                continue
            }
            picked.add(item)
            perGroup[key] = used + 1
        }
        return picked
    }
}
