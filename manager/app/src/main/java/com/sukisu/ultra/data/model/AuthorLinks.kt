/*
 * Copyright (C) 2025 SukiSU Ultra
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sukisu.ultra.data.model

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

/**
 * 作者主页链接的归一化。
 *
 * 索引里的这两个字段由上传者手填，同一个主页能写出七八种样子：带不带协议、带不带 www、
 * 末尾多一条斜杠、从浏览器地址栏复制来的 query/fragment。归一化的目的不是「美化」，
 * 而是让客户端、网页、Worker 三处对「这个值算不算合法」给出同一个答案——放行一个服务端
 * 会拒的形状，等于让用户白填一遍；反过来拦下服务端认得的写法，则让人莫名其妙传不上去。
 *
 * 上传者自己的主页链接不在这里：它由 UID 现拼（`https://www.coolapk.com/u/<uid>`），不落库，
 * 也不该被当成「作者主页」。
 */
object AuthorLinks {

    private const val COOLAPK_HOST = "www.coolapk.com"

    /** 索引里允许省略 `www.`；输出统一补回去。 */
    private const val COOLAPK_BARE_HOST = "coolapk.com"

    private const val GITHUB_HOST = "github.com"

    /** 酷安 UID 是纯数字，4–12 位（与契约一致）。 */
    private val COOLAPK_UID_RE = Regex("^[0-9]{4,12}$")

    /**
     * GitHub login 的字符规则（与 GitHub 自己一致）：1–39 个字母/数字/连字符，
     * 首尾不能是连字符；连续两个连字符另外单独判（正则里的中段字符允许单个 `-`）。
     */
    private val GITHUB_LOGIN_RE = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?$")

    /**
     * GitHub 占用的单段路径，不是用户名。
     *
     * 这些值如果被当成 login 放过去，链接会打开设置页、市场页或登录页——用户以为填对了，
     * 点开却是别的东西。大小写不敏感：GitHub 的路由不分大小写。
     */
    private val GITHUB_RESERVED = setOf(
        "about", "account", "admin", "api", "apps", "blog", "business", "codespaces",
        "collections", "contact", "customer-stories", "dashboard", "developer", "discussions",
        "download", "downloads", "edu", "education", "enterprise", "events", "explore",
        "features", "followers", "following", "gist", "git", "git-guides", "github", "help",
        "home", "hovercards", "issues", "join", "login", "logout", "marketplace", "mobile",
        "new", "nonprofit", "notifications", "open-source", "organizations", "orgs", "pages",
        "partners", "personal", "plans", "prebuilds", "pricing", "privacy", "projects", "pulls",
        "readme", "resources", "search", "security", "sessions", "settings", "shop", "showcases",
        "signup", "site", "solutions", "sponsors", "stars", "status", "team", "teams", "terms",
        "topics", "trending", "users", "watching", "welcome", "wiki", "workflows",
    )

    /**
     * 把一条酷安主页归一化成 `https://www.coolapk.com/u/<uid>`；不合法返回 null。
     *
     * 只认 `coolapk.com` 与 `www.coolapk.com` 两个 host，路径只认 `u/<4–12 位数字>`：
     * 帖子、话题、用户动态都不是「作者主页」，放过去会把作者区块指向一个不相干的地方。
     */
    fun normalizeCoolapk(raw: String): String? {
        val url = parseHttpUrl(raw) ?: return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        if (url.query != null || url.fragment != null) return null
        val host = url.host.lowercase(Locale.ROOT)
        if (host != COOLAPK_HOST && host != COOLAPK_BARE_HOST) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size != 2 || segments[0] != "u") return null
        val uid = segments[1]
        if (!COOLAPK_UID_RE.matches(uid)) return null
        return "https://$COOLAPK_HOST/u/$uid"
    }

    /**
     * 把一条 GitHub 主页归一化成 `https://github.com/<login>`；不合法返回 null。
     *
     * login 保留原始大小写（GitHub 的 URL 不分大小写，但用户填的是哪个就显示哪个，
     * 不在客户端替它改名）。仓库、gist、`www.github.com` 都不算主页：仓库地址是另一栏的事。
     */
    fun normalizeGithub(raw: String): String? {
        val url = parseHttpUrl(raw) ?: return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        if (url.query != null || url.fragment != null) return null
        if (url.host.lowercase(Locale.ROOT) != GITHUB_HOST) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size != 1) return null
        val login = segments[0]
        if (!GITHUB_LOGIN_RE.matches(login)) return null
        // 正则允许中段出现连字符，但 GitHub 不允许连续两个。
        if ("--" in login) return null
        if (login.lowercase(Locale.ROOT) in GITHUB_RESERVED) return null
        return "https://$GITHUB_HOST/$login"
    }

    /**
     * 解析成 [HttpUrl]：缺协议时按 https 补。
     *
     * `toHttpUrlOrNull` 只认 http/https，`ftp://` 之类会直接解析失败；这里再显式挡一次
     * 其它协议，免得哪天换成更宽松的解析器把 `javascript:` 之类放进来。
     */
    private fun parseHttpUrl(raw: String): HttpUrl? {
        val text = raw.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val scheme = text.substringBefore("://", missingDelimiterValue = "")
        if (scheme.isNotEmpty() && scheme.lowercase(Locale.ROOT) !in SCHEMES) return null
        val candidate = if (scheme.isEmpty()) "https://$text" else text
        val url = candidate.toHttpUrlOrNull() ?: return null
        // 非默认端口一律不认：主页链接没有理由带端口，带了多半是拼错或钓鱼。
        // HttpUrl 会把省略的端口补成协议默认值，所以这两种之外都算显式写了端口。
        if (url.port != 80 && url.port != 443) return null
        return url
    }

    private val SCHEMES = setOf("http", "https")
}
