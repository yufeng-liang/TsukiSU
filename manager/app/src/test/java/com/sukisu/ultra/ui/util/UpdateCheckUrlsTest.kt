package com.sukisu.ultra.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckUrlsTest {

    @Test
    fun triesDirectApiFirstThenMirrors() {
        val urls = updateCheckUrls()
        assertEquals(3, urls.size)
        assertTrue(urls[0], urls[0].startsWith("https://api.github.com/repos/"))
        assertTrue(urls[0].endsWith("/releases/latest"))
        // 镜像规则是「整个 api 地址拼在代理域名后面」：实测 gh-proxy.org / gh-proxy.com 返回 200。
        assertEquals("https://gh-proxy.org/" + urls[0], urls[1])
        assertEquals("https://gh-proxy.com/" + urls[0], urls[2])
    }

    @Test
    fun pointsAtForkRepoNotRenamedUpstream() {
        // SELF_UPDATE_REPO 用旧名也能拿到 301 重定向后的同一个 release，换名时不需要改这里。
        assertTrue(updateCheckUrls()[0].contains("yufeng-liang/"))
    }
}
