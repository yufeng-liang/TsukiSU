package com.sukisu.ultra.data.model

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 源码仓库地址的归一化：只认 GitHub 的 `<owner>/<repo>` 两段路径。
 *
 * 归一化的结果是服务端与网页端共用的那一种写法（`https://github.com/<owner>/<repo>`），
 * 这样 App 本地能过的输入，服务端一定也能过；反过来 App 不认的形状，提交前就在本地
 * 拦住，不用白传一整包再收一个 400。
 *
 * 刻意不接受的：query、fragment、端口、userinfo、多段路径（`/tree/main` 那种让人
 * 一眼以为是仓库、实际指向某个子目录的地址）、以及非 github.com 的域名。后者的判定
 * 用精确相等而不是 `endsWith`——`evil-github.com` 这类冒名域名必须拒掉。
 */
object GitHubRepoAddress {

    private const val HOST = "github.com"

    /** GitHub 的用户名（同时用于组织名）：字母数字与连字符，不以连字符开头或结尾。 */
    private val OWNER_RE = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?$")

    /** 仓库名：字母数字、点、下划线、连字符，最长 100。 */
    private val REPO_RE = Regex("^[A-Za-z0-9._-]{1,100}$")

    /**
     * 把用户输入（或索引里的 `repoUrl`）归一化成 `https://github.com/<owner>/<repo>`。
     *
     * 接受：完整 https/http 地址、省略协议的 `github.com/o/r`、结尾多一个 `/`、仓库名
     * 带 `.git` 后缀；owner/repo 的大小写保留原样——GitHub 的路径虽然大小写不敏感，
     * 但换大小写会让人对不上仓库页地址。
     *
     * 返回 null 表示这不是一个可用的 GitHub 仓库地址。
     */
    fun normalize(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val candidate = when {
            text.startsWith("https://") || text.startsWith("http://") -> text
            // 省略协议时补 https 再判，和出处那一栏同一条规则。
            !text.contains("://") -> "https://$text"
            else -> return null
        }
        val url = candidate.toHttpUrlOrNull() ?: return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        if (url.query != null || url.fragment != null) return null
        // 端口只允许协议默认值：仓库地址没有端口的语义。
        val defaultPort = if (url.scheme == "https") 443 else 80
        if (url.port != defaultPort) return null
        val host = url.host.lowercase()
        if (host != HOST) return null

        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size != 2) return null
        val owner = segments[0]
        val repo = segments[1].removeSuffix(".git")
        if (repo.isEmpty() || repo == "." || repo == "..") return null
        if (!OWNER_RE.matches(owner) || !REPO_RE.matches(repo)) return null
        return "https://$HOST/$owner/$repo"
    }

    /** 归一化后的 `owner/repo`，用在「比较两次输入是不是同一个仓库」这种地方。 */
    fun ownerRepo(raw: String): String? = normalize(raw)?.removePrefix("https://$HOST/")
}
