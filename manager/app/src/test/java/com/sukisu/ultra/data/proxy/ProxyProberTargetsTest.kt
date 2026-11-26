package com.sukisu.ultra.data.proxy

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 设置页测速的目标组装测试（纯函数，不碰网络）。
 *
 * 直连必须和代理一起进入同一轮探测，否则设置页的延迟排序和自动选优看到的不是同一批通道。
 */
class ProxyProberTargetsTest {

    private val releaseUrl = "https://github.com/owner/repo/releases/download/v1.0.0/module.zip"
    private val proxy1 = ProxyEntry("p1", "gh-proxy", "https://gh-proxy.org")
    private val proxy2 = ProxyEntry("p2", "ghproxy", "https://ghproxy.net")

    @Test
    fun includeDirectAddsOfficialChannelFirst() {
        val targets = ProxyProberImpl.buildProbeTargets(
            entries = listOf(proxy1, proxy2),
            sampleUrl = releaseUrl,
            includeDirect = true,
        )

        assertEquals(listOf(DIRECT_ID, proxy1.id, proxy2.id), targets.map { it.first })
        assertEquals(releaseUrl, targets[0].second)
        assertEquals(ProxyUrl.build(proxy1, releaseUrl), targets[1].second)
    }

    @Test
    fun excludeDirectKeepsOnlyProxyChannels() {
        val targets = ProxyProberImpl.buildProbeTargets(
            entries = listOf(proxy1),
            sampleUrl = releaseUrl,
            includeDirect = false,
        )

        assertEquals(listOf(proxy1.id), targets.map { it.first })
    }

    @Test
    fun directFallsBackToGithubRootWithoutSampleUrl() {
        val expected = "https://github.com"
        val targets = ProxyProberImpl.buildProbeTargets(
            entries = emptyList(),
            sampleUrl = "  ",
            includeDirect = true,
        )

        assertEquals(listOf(DIRECT_ID to expected), targets)
    }

    @Test
    fun proxiesUseBaseUrlWithoutSampleUrl() {
        val targets = ProxyProberImpl.buildProbeTargets(
            entries = listOf(proxy1),
            sampleUrl = null,
            includeDirect = false,
        )

        assertEquals(listOf(proxy1.id to proxy1.baseUrl), targets)
    }
}
