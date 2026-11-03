package com.sukisu.ultra.data.proxy

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.sukisu.ultra.ksuApp
import org.json.JSONArray

/** 单独一份 prefs：与设置页的主 prefs（`settings`）分开，两者的生命周期不一样。 */
private const val PREFS_NAME = "proxy_prefs"
private const val KEY_MODE = "mode"
private const val KEY_PINNED_ID = "pinned_id"
private const val KEY_LAST_GOOD_ID = "last_good_id"
private const val KEY_LAST_GOOD_SPEED = "last_good_speed"
private const val KEY_LAST_GOOD_AT = "last_good_at"
private const val KEY_RANKING = "ranking"
private const val KEY_RANKING_AT = "ranking_at"
private const val KEY_RANKING_NETWORK = "ranking_network"

/**
 * 竞速排序的可信期。
 *
 * 一小时是「别让每次下载都花 8 秒测速」与「别拿早上的排序去下晚上的包」之间的折中：同一批
 * 代理的相对快慢不会在几分钟内翻转，但换了网络、或者某个节点被限速，一小时足够重新测出来。
 */
private const val RANKING_TTL_MS = 60L * 60 * 1000

/**
 * 用户选择与粘性记忆。
 *
 * 用独立 prefs（`proxy_prefs`，**不是**设置页的 `settings`）是有意的：这些值决定「怎么联网」，
 * 用户清一次设置不该把「上次哪个代理跑得通」也一起抹掉——那会让下一次下载重新从最慢的代理试起。
 *
 * 写操作一律 `apply()`：这些值都是纯记忆，丢一次最多是多试一个通道，不值得为它阻塞调用线程。
 */
class ProxyPreferenceImpl(
    context: Context = ksuApp,
) : ProxyPreference {

    /** 持有 Application 而不是传进来的 Activity：这个实例活得比任何界面都久。 */
    private val appContext: Context = context.applicationContext ?: context

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun mode(): ProxyMode = ProxyMode.from(prefs.getString(KEY_MODE, null))

    override fun setMode(mode: ProxyMode) {
        prefs.edit().putString(KEY_MODE, mode.value).apply()
    }

    override fun pinnedId(): String? = prefs.getString(KEY_PINNED_ID, null)?.takeIf { it.isNotEmpty() }

    /** 传 null 表示「不固定」，把键删掉而不是写空串：空串会被下一次读当成一个真实的代理 id。 */
    override fun setPinnedId(id: String?) {
        val editor = prefs.edit()
        if (id.isNullOrEmpty()) editor.remove(KEY_PINNED_ID) else editor.putString(KEY_PINNED_ID, id)
        editor.apply()
    }

    override fun lastGoodId(): String? = prefs.getString(KEY_LAST_GOOD_ID, null)?.takeIf { it.isNotEmpty() }

    override fun recordSuccess(entry: ProxyEntry, speedBytesPerSec: Long) {
        prefs.edit()
            .putString(KEY_LAST_GOOD_ID, entry.id)
            .putLong(KEY_LAST_GOOD_SPEED, speedBytesPerSec)
            .putLong(KEY_LAST_GOOD_AT, System.currentTimeMillis())
            .apply()
    }

    /**
     * 只在这个代理正是「上次成功」的那个时才清记忆。
     *
     * 别的代理失败不影响它：一次下载可能先后试了好几个候选，被淘汰的是当前这个，而上次真正
     * 跑通的那个仍然值得优先复用。清掉速度与时间是为了不留下「有记录但已经不可用」的假象。
     *
     * 同时把它从竞速排序里摘掉（等于降到队尾）：否则一个刚挂掉的代理会在接下来一小时里继续
     * 排第一，每次下载都先撞它一次。摘除本身就是一条新事实，顺手刷新时间戳没有坏处。
     */
    override fun recordFailure(entryId: String) {
        val order = ranking()
        if (entryId in order) {
            setRanking(order.filterNot { it == entryId })
        }
        if (lastGoodId() != entryId) return
        prefs.edit()
            .remove(KEY_LAST_GOOD_ID)
            .remove(KEY_LAST_GOOD_SPEED)
            .remove(KEY_LAST_GOOD_AT)
            .apply()
    }

    /**
     * 上次竞速出来的通道顺序（含 `direct`）。
     *
     * 存成 JSON 数组而不是 `StringSet`：集合不保序，而这份数据唯一的意义就是顺序。也存过
     * 用分隔符拼的字符串，但那要求 id 里永远不出现分隔符——不值为一个运营可改的字段赌这个。
     */
    override fun ranking(): List<String> {
        val raw = prefs.getString(KEY_RANKING, null)
        if (raw.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val out = ArrayList<String>(array.length())
        for (index in 0 until array.length()) {
            val id = array.optString(index, "").trim()
            if (id.isNotEmpty()) out.add(id)
        }
        return out
    }

    /**
     * 排序是否仍然可信：同一网络指纹、且不超过 [RANKING_TTL_MS]。
     *
     * 换了网络就作废是重点：家里 WiFi 上最快的代理，切到移动网络后名次可能完全反过来。
     *
     * 时间戳在「未来」（系统时钟被往回拨过）时 `age` 为负，这时当成仍然新鲜而不是过期：数据
     * 是我们自己写进去的，只是系统时间离谱，不该因此每次下载都重测 8 秒。宁可让一份旧排序多
     * 活一阵，也不能让一块时间不准的设备把每次下载都拖慢。
     */
    override fun isRankingFresh(): Boolean {
        val measuredAt = prefs.getLong(KEY_RANKING_AT, 0L)
        if (measuredAt <= 0L) return false
        if (System.currentTimeMillis() - measuredAt > RANKING_TTL_MS) return false
        return prefs.getString(KEY_RANKING_NETWORK, null) == networkKey()
    }

    override fun setRanking(order: List<String>) {
        val array = JSONArray()
        order.filter { it.isNotEmpty() }.distinct().forEach { array.put(it) }
        prefs.edit()
            .putString(KEY_RANKING, array.toString())
            .putLong(KEY_RANKING_AT, System.currentTimeMillis())
            .putString(KEY_RANKING_NETWORK, networkKey())
            .apply()
    }

    /**
     * 当前网络的粗粒度指纹：只分 WIFI / MOBILE / ETHERNET / VPN / NONE。
     *
     * 不取 SSID、不取运营商：那需要定位权限，而这里只需要回答「换网了没有」——同一台机器在
     * 家里 WiFi 上跑得通的代理，到了移动网络未必还行，粘性记忆得跟着网络走。
     *
     * VPN 单列一类：VPN 网络只报 TRANSPORT_VPN，不报底层传输方式，落到 else 会变成 NONE——
     * 那样「开梯子」与「断网」会共用同一个指纹，开关梯子永远不触发重测。单列之后，
     * 开/关梯子至少会被认成换了网络；两个不同的 VPN 之间切换仍然分不出来（系统不给底层信息），
     * 这是刻意的取舍，不为此去申请额外权限。
     *
     * `activeNetwork` / `getNetworkCapabilities` 都是 API 23 起，项目 minSdk 26，无需再做版本分支。
     */
    override fun networkKey(): String {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NETWORK_NONE
        val network = manager.activeNetwork ?: return NETWORK_NONE
        val capabilities = manager.getNetworkCapabilities(network) ?: return NETWORK_NONE
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return NETWORK_VPN
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "MOBILE"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> NETWORK_NONE
        }
    }

    private companion object {
        const val NETWORK_NONE = "NONE"
        const val NETWORK_VPN = "VPN"
    }
}
