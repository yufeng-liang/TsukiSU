package com.sukisu.ultra.ui.screen.upload

import android.content.Context
import androidx.core.content.edit
import com.sukisu.ultra.data.backup.CredentialCipher
import com.sukisu.ultra.data.backup.KeystoreKeyProvider
import com.sukisu.ultra.data.backup.sha256Hex
import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.util.UploadSession
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 上传表单记住的「上次用的酷安 UID」。
 *
 * 只记这一项：它是每次都要重新输入、又几乎不变的那一个字段。出处、勾选、口令都不记——
 * 那些是「这一次上传」的知情与承诺，跨次复用等于替用户提前签了字。
 *
 * key 与读写单独放出来，和 [com.sukisu.ultra.data.repository.ModuleContributionPrefs]
 * 同一个理由：key 一改名，老用户会静默回到空输入，而这不是一个能被察觉的错误。
 *
 * ── 为什么令牌单独一个 prefs 文件 ──────────────────────────────────────────────
 *
 * 令牌是**九十天有效的凭据**（服务端 KV 里只存它的 sha256，拿到原文就能冒充这个 UID
 * 提交）。而 Android 的自动备份会把 sharedpref 整份拷进 Google Drive / 换机传输 ——
 * 凭据离开这台设备，等于把「这台设备已验证」变成「任何恢复过这份备份的设备都验证过」。
 *
 * 备份规则只能按**文件**粒度排除，不能按 key 粒度：所以要么把令牌挪进独立文件
 * （`res/xml/backup_rules.xml` 与 `res/xml/data_extraction_rules.xml` 排除它），
 * 要么整份 `settings.xml` 不备份 —— 后者会把 WebDAV 配置、主题、各种开关一起丢掉。
 * 前者的代价写在下面 [PREFS_NAME] 的注释里。
 *
 * 落盘内容现为 **Keystore AES/GCM 信封加密**（`data/backup/CredentialCipher.kt` +
 * `KeystoreKeyProvider`，独立别名见 [KEY_ALIAS]）：盘上不再是明文，文件本身仍整份
 * 排除备份 —— 加密解决「盘上明文」，排除解决「凭据离开设备」，两条独立的防线，都要。
 *
 * 降级：Keystore 不可用（个别 ROM 砍了 AndroidKeyStore）时明文落盘，功能不因加密失败
 * 而不可用 —— 备份排除仍然兜着云备份那条路。旧版本写下的明文在读到时顺手迁成信封
 * （见 [readSession]）；信封在而密钥没了（系统清了 Keystore）当没存过，界面回到
 * 「要改签名」，不会拿坏信封去撞服务端。
 */
object ModuleUploadPrefs {

    /**
     * 独立 prefs 文件名。**只放会话令牌那几个键与身份验证串**，不放 UID。
     *
     * 改名（从 `settings` 到这个名字）意味着老用户盘上那一张令牌读不到了 ——
     * 一次性的代价：他们下次投稿要再改一次签名，之后又是九十天。这是刻意的取舍：
     * 把一张九十天凭据留在「会被云备份拷走」的文件里，比让老用户重验一次更贵。
     * 读不到就是读不到，没有迁移代码：`readSession()` 见到空串就返回 null，
     * 界面回到「要改签名」那条路，不需要任何特殊分支。
     */
    private const val PREFS_NAME = "module_upload_session"

    const val KEY_LAST_UID = "module_upload_last_uid"

    // ---- 长期会话令牌（一次改签名，九十天免改）----
    //
    // 四个值分开存、都带类型：JSON 里塞一个对象在 SharedPreferences 上不可读，
    // 出问题时（比如令牌串被手改成 63 位）也没法只坏一处。分开存之后
    // `readSession()` 能逐项判定，坏哪一项就整份当没有。
    const val KEY_SESSION_TOKEN = "module_upload_session_token"
    const val KEY_SESSION_UID = "module_upload_session_uid"
    const val KEY_SESSION_NICK = "module_upload_session_nickname"
    const val KEY_SESSION_AVATAR = "module_upload_session_avatar"
    const val KEY_SESSION_EXPIRES_AT = "module_upload_session_expires_at"
    // 评论级令牌的等级（0..1 = 只够点赞评论，≥2 = 可投稿，-1 = 旧盘没有这个键）。
    // 独立一个键而不是塞进 token：旧盘没有它，读到 -1 就按旧逻辑走，不做迁移。
    const val KEY_SESSION_LEVEL = "module_upload_session_level"

    // ---- 身份验证串（哈希承诺，九十天内复用）----
    //
    // 与令牌放在同一份 prefs 文件里：它同样是「这台设备已验证过」这件事的依据，
    // 被云备份拷走等于换台机器也能直接换令牌（见文件头那段取舍）。
    const val KEY_IDENTITY_SECRET = "module_upload_identity_secret"

    // ---- 吊销未遂的待办 ----
    //
    // 服务端那张令牌是九十天有效的凭据：本地抹掉只解决「这台设备不再记住我」，
    // 解决不了「那张令牌本身还能用」。登出时吊销失败（断网、超时、服务端抽风）如果
    // 就此丢掉，用户以为自己退了，那张令牌却还能被人用满九十天。所以失败的那一张先
    // 记在盘上，下次进投稿链路时顺手重试 —— 服务端吊销是幂等的（令牌不存在也回 ok，
    // 见 worker 的 /session/revoke），重试没有副作用。
    const val KEY_PENDING_REVOKE = "module_upload_pending_revoke"

    /** 待吊销队列最多记这么多张：它是「还没办完的凭据」，不是流水账。 */
    private const val PENDING_REVOKE_LIMIT = 20


    // ---- 落盘加密 ----
    //
    // 令牌与身份验证串写盘前过一层 Keystore 信封（[CredentialCipher]，格式 `v1:iv:ct`）。
    // 判定「是不是信封」只认前缀 `v1:`：旧版明文没有前缀，原样读回后顺手迁移。
    // 别名独立于 WebDAV 那把（"sukisu_backup_webdav"）：一把被清不连坐另一把。
    private const val KEY_ALIAS = "sukisu_upload_session"

    /** [CredentialCipher] 信封的版本前缀；区分「加密落盘」与「旧版明文/降级明文」。 */
    private const val ENVELOPE_PREFIX = "v1:"

    /** 身份验证串的字节数：16 字节 → 32 位小写十六进制，与契约的 `^[0-9a-f]{32}$` 一致。 */
    private const val SECRET_BYTES = 16

    /** 契约 §4：承诺串是 `sha256("sukisu-identity-v2:" + secret)` 的前 24 位。 */
    private const val IDENTITY_COMMIT_PREFIX = "sukisu-identity-v2:"
    const val IDENTITY_COMMIT_LEN = 24

    private val SECRET_RANDOM = SecureRandom()

    /** 与 [com.sukisu.ultra.ui.util.ModuleContribution.isValidUid] 的 `\d{4,12}` 同一条形状。 */
    private const val UID_MAX_LEN = 12

    /** 输入框的过滤：只留数字并截到 12 位（纯函数，单测直接锁住）。 */
    fun sanitizeUid(raw: String): String = raw.filter { it.isDigit() }.take(UID_MAX_LEN)

    /** 读上次的 UID；没存过或存坏了都返回空串（空串在界面上就是「没填」）。 */
    fun lastUid(): String = runCatching {
        prefs()?.getString(KEY_LAST_UID, null).orEmpty()
    }.getOrDefault("")

    /** 记下这次核实通过的 UID。写失败不影响本次上传。 */
    fun rememberUid(uid: String) {
        val cleaned = sanitizeUid(uid)
        if (cleaned.isEmpty()) return
        runCatching { prefs()?.edit { putString(KEY_LAST_UID, cleaned) } }
    }

    /**
     * 读本地存的那一份长期令牌；没存过、存坏了、或者已经到期，都返回 null。
     *
     * 到期判定放在这一层而不是界面：界面每次重组都会问一次，判据只有一处才不会
     * 出现「一个界面说过期、另一个说没过期」。真正的作数方仍是服务端 ——
     * 本地改 `expiresAt` 不会延长任何东西，只会让 App 更早/更晚去问一次。
     */
    fun readSession(now: Long = System.currentTimeMillis()): UploadSession? = runCatching {
        val p = sessionPrefs() ?: return null
        val rawToken = p.getString(KEY_SESSION_TOKEN, null).orEmpty()
        val session = UploadSession(
            token = unseal(rawToken),
            uid = p.getString(KEY_SESSION_UID, null).orEmpty(),
            nickname = p.getString(KEY_SESSION_NICK, null).orEmpty(),
            avatar = p.getString(KEY_SESSION_AVATAR, null).orEmpty(),
            expiresAt = p.getLong(KEY_SESSION_EXPIRES_AT, 0L),
            level = p.getInt(KEY_SESSION_LEVEL, -1),
        )
        val valid = session.validAt(now)
        // 旧版本写下的明文：读到（且这份令牌还活着）就顺手迁成信封 —— 读路径上唯一一次
        // 写，幂等；迁移失败不影响本次返回，下次读会再试。
        if (valid && rawToken.isNotEmpty() && !rawToken.startsWith(ENVELOPE_PREFIX)) {
            runCatching { p.edit { putString(KEY_SESSION_TOKEN, seal(rawToken)) } }
        }
        if (!valid) {
            // 令牌按时间到点了：盘上那一份已经没有用 —— 但它不会自己消失，而评论区与「我的
            // 评论」只在「会话变更」时才重读（见 sessionVersion）。不在这里抹掉并喊一声，
            // 用户停在评论页会一直看到「已登录」、红心还亮着，直到他离开再回来。
            // 只有确实存过东西才走这一步：没存过的设备不该因为一次读就写一次盘。
            if (rawToken.isNotEmpty()) clearSession()
            null
        } else {
            session
        }
    }.getOrNull()

    /** 存下一张令牌。形状不对的一律不存（脏数据不许落到盘上）；写成功就喊一声（见 sessionVersion）。 */
    fun writeSession(session: UploadSession) {
        if (!session.wellFormed) return
        runCatching {
            sessionPrefs()?.edit {
                putString(KEY_SESSION_TOKEN, seal(session.token))
                putString(KEY_SESSION_UID, session.uid)
                putString(KEY_SESSION_NICK, session.nickname)
                putString(KEY_SESSION_AVATAR, session.avatar)
                putLong(KEY_SESSION_EXPIRES_AT, session.expiresAt)
                putInt(KEY_SESSION_LEVEL, session.level)
            }
        }
        bumpSessionVersion()
    }

    /**
     * 「退出验证」：把本地这一份抹掉。服务端那张另外吊销（见 ViewModel）。
     *
     * 抹完喊一声（见 sessionVersion）：评论区的 ViewModel 各自握着内存里的令牌快照，
     * 不喊的话它们会继续显示「已登录」，直到用户离开再回来。
     */
    fun clearSession() {
        runCatching {
            sessionPrefs()?.edit {
                remove(KEY_SESSION_TOKEN)
                remove(KEY_SESSION_UID)
                remove(KEY_SESSION_NICK)
                remove(KEY_SESSION_AVATAR)
                remove(KEY_SESSION_EXPIRES_AT)
                remove(KEY_SESSION_LEVEL)
            }
        }
        bumpSessionVersion()
    }

    // ---- 会话变更信号 ----

    private val _sessionVersion = MutableStateFlow(0L)

    /**
     * 会话身份版本号：写下新令牌、抹掉令牌（登出）各 +1。只在进程内有效，不落盘。
     *
     * 「登出」发生在投稿链路的 ViewModel 里，而评论区、我的评论页各有自己的 ViewModel ——
     * 它们不会因为别人清了盘就自动作废。与其在每个页面各写一套「记得重读」，不如让改
     * 会话的那一处喊一声：收集方收这个 Flow，用 drop(1) 跳过当前值，只在**变化**时重读
     * 会话（当前值属于「页面自己首次读取」的事）。
     */
    val sessionVersion: StateFlow<Long> = _sessionVersion.asStateFlow()

    /** 会话身份变了：喊一声（见 [sessionVersion]）。 */
    private fun bumpSessionVersion() {
        _sessionVersion.update { it + 1 }
    }

    // ---- 吊销未遂的待办（见 [KEY_PENDING_REVOKE]）----

    /** 盘上那几张待吊销令牌的**信封串**（未解密；Editor 没有读方法，所以读在 edit 之外）。 */
    private fun pendingRevokeEnvelopes(): Set<String> = runCatching {
        sessionPrefs()?.getStringSet(KEY_PENDING_REVOKE, emptySet()).orEmpty()
    }.getOrDefault(emptySet())

    /**
     * 记一张「吊销没成功」的令牌：信封加密后落盘，与其它凭据同一条处理。
     *
     * 三处讲究：解不开的旧信封（换过密钥）顺手清掉 —— 它们再也吊销不掉，留着只会让每次
     * 读都白解一遍；同一张令牌不重复记（每张信封的 IV 不同，得解出原文才比得出重复）；
     * 拿不到密钥时 seal 会降级成明文，那就**不记** —— 宁可这一条不留痕，也不在盘上多存
     * 一份明文令牌。
     */
    suspend fun addPendingRevoke(token: String) {
        withContext(Dispatchers.IO) {
            val pending = token.trim()
            if (pending.isBlank()) return@withContext
            val alive = pendingRevokeEnvelopes().filter { unseal(it).isNotBlank() }
            if (alive.any { unseal(it) == pending }) return@withContext
            val sealed = seal(pending)
            if (sealed == pending) return@withContext
            val next = (alive + sealed).takeLast(PENDING_REVOKE_LIMIT).toSet()
            runCatching { sessionPrefs()?.edit { putStringSet(KEY_PENDING_REVOKE, next) } }
        }
    }

    /** 还没吊销掉的令牌原文；解不开的（密钥没了）当没记过。信封解密要碰 Keystore，放 IO 上做。 */
    suspend fun pendingRevokes(): List<String> = withContext(Dispatchers.IO) {
        pendingRevokeEnvelopes().map { unseal(it) }.filter { it.isNotBlank() }
    }

    /**
     * 一张待吊销的令牌办完了（或确认不必再试）：划掉它。
     *
     * 每次都得解出原文比对：每张信封的 IV 不同，比信封串是看不出重复的。解不开的那些
     * （密钥没了、信封坏了）也一并清掉 —— 它们永远办不完，留着只是每次读都白解一遍。
     */
    suspend fun removePendingRevoke(token: String) {
        withContext(Dispatchers.IO) {
            val pending = token.trim()
            if (pending.isBlank()) return@withContext
            val next = pendingRevokeEnvelopes()
                .filterNot { unseal(it) == pending }
                .filter { unseal(it).isNotBlank() }
                .toSet()
            runCatching {
                sessionPrefs()?.edit {
                    if (next.isEmpty()) remove(KEY_PENDING_REVOKE) else putStringSet(KEY_PENDING_REVOKE, next)
                }
            }
        }
    }

    /**
     * 读本地的身份验证串；没存过、存坏了都返回空串（空串在界面上就是「还没生成」）。
     *
     * 形状判定与服务端同一条（`^[0-9a-f]{32}$`）：盘上那串被手改坏了，与其发出去换一个
     * `identity_invalid`，不如当它不存在、由界面重新生成一串。
     */
    fun identitySecret(): String = runCatching {
        val secret = unseal(sessionPrefs()?.getString(KEY_IDENTITY_SECRET, null).orEmpty())
        secret.takeIf { SECRET_HEX_RE.matches(it) }.orEmpty()
    }.getOrDefault("")

    /**
     * 生成一串新的身份验证串并落盘。
     *
     * 只在两处调用：本地还没有（或存坏了）、以及用户主动点「换一串」。**不在令牌到期时换**——
     * 换一串就要用户再改一次酷安签名，那正是哈希承诺要省掉的动作。
     */
    fun renewIdentitySecret(): String {
        val bytes = ByteArray(SECRET_BYTES)
        SECRET_RANDOM.nextBytes(bytes)
        val secret = bytes.joinToString("") { "%02x".format(it) }
        runCatching { sessionPrefs()?.edit { putString(KEY_IDENTITY_SECRET, seal(secret)) } }
        return secret
    }

    /** 拿本地那串；没有就先生成一串。 */
    fun ensureIdentitySecret(): String = identitySecret().ifEmpty { renewIdentitySecret() }

    /**
     * 承诺串：用户抄进酷安签名的那一行。
     *
     * 与服务端 [com.sukisu.ultra.ui.util.ModuleContribution] 契约同一算法——
     * `sha256("sukisu-identity-v2:" + secret)` 的前 24 位。签名里只有这一串，
     * 反推不出 secret，所以别人看见签名也领不走令牌。
     */
    fun identityCommitment(secret: String): String =
        (IDENTITY_COMMIT_PREFIX + secret).toByteArray(Charsets.UTF_8)
            .sha256Hex()
            .take(IDENTITY_COMMIT_LEN)

    /**
     * 「上次用的 UID」仍然在 `settings` 里：它不是凭据，不需要跟着令牌搬家。
     *
     * 两套 prefs 分开取，是为了让「哪个键在哪一份文件里」只在这一处体现 ——
     * 备份规则排除的是 [PREFS_NAME] 那一份，UID 不受影响。
     */
    private fun prefs() = runCatching {
        ksuApp.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }.getOrNull()

    /** 令牌专用的那一份 prefs（被备份规则排除，见文件头）。 */
    private fun sessionPrefs() = runCatching {
        ksuApp.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }.getOrNull()

    // ---- 信封读写（见文件头「落盘加密」）----
    //
    // 两条失败路径都是「降级到明文」或「当没存过」，不抛、不重试：
    // 加密是纵深防御，不能反过来把投稿功能锁死。

    /** Keystore 密钥；拿不到（个别 ROM 没有 AndroidKeyStore）返回 null。 */
    private fun cipherKey() = runCatching { KeystoreKeyProvider.key(KEY_ALIAS) }.getOrNull()

    /** 写盘前过信封；拿不到密钥（或加密失败）就明文落盘，备份排除仍兜着云备份。 */
    private fun seal(plain: String): String {
        val key = cipherKey() ?: return plain
        return runCatching { CredentialCipher.encrypt(key, plain) }.getOrDefault(plain)
    }

    /** 读盘时拆信封；明文（旧版/降级写入）原样返回，信封坏/密钥没了返回空串（当没存过）。 */
    private fun unseal(stored: String): String {
        if (!stored.startsWith(ENVELOPE_PREFIX)) return stored
        val key = cipherKey() ?: return ""
        return runCatching { CredentialCipher.decrypt(key, stored) }.getOrDefault("")
    }

    /** 身份验证串的形状：与服务端 `IDENTITY_SECRET_RE` 同一条尺子。 */
    private val SECRET_HEX_RE = Regex("^[0-9a-f]{32}$")
}
