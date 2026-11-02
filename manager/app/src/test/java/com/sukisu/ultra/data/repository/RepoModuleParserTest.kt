package com.sukisu.ultra.data.repository

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepoModuleParserTest {

    private val ksuJson = """
        [
          {
            "moduleId": "zygisk_assistant",
            "moduleName": "Zygisk Assistant",
            "authors": [{"name": "jijingg", "link": "https://github.com/jijingg"}],
            "summary": "A Zygisk module",
            "metamodule": false,
            "zygisk": true,
            "stargazerCount": 42,
            "updatedAt": "2025-06-01T00:00:00Z",
            "createdAt": "2024-01-01T00:00:00Z",
            "latestRelease": {
              "name": "v2.2",
              "time": "2025-06-01T00:00:00Z",
              "versionCode": 202,
              "downloadUrl": "https://example.com/ZygiskAssistant-v2.2.zip"
            }
          }
        ]
    """.trimIndent()

    private val mmrlJson = """
        {
          "id": "test-repo",
          "name": "Test Repo",
          "modules": [
            {
              "id": "CustomPinyinDictionary",
              "name": "CustomPinyinDictionary for Gboard",
              "version": "v20260101",
              "versionCode": 20260101,
              "author": "wuhgit",
              "description": "Gboard custom pinyin dictionary",
              "support": "https://github.com/wuhgit/CustomPinyinDictionary/issues",
              "readme": "https://raw.githubusercontent.com/wuhgit/CustomPinyinDictionary/main/README.md",
              "timestamp": 1767223192.0,
              "versions": [
                {
                  "timestamp": 1735696699.0,
                  "version": "v20250101",
                  "versionCode": 20250101,
                  "zipUrl": "https://example.com/dict_v20250101.zip",
                  "changelog": "https://example.com/v20250101.md",
                  "size": 31355148
                },
                {
                  "timestamp": 1767223192.0,
                  "version": "v20260101",
                  "versionCode": "20260101",
                  "zipUrl": "https://example.com/dict_v20260101.zip",
                  "changelog": "https://example.com/v20260101.md",
                  "size": 30270378
                }
              ]
            },
            {
              "id": "no_versions_module",
              "name": "No Versions",
              "author": "someone",
              "description": "module without versions"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `sniffFormat detects kernelsu array`() {
        assertEquals(RepoFormat.KERNELSU, RepoModuleParser.sniffFormat(ksuJson))
    }

    @Test
    fun `sniffFormat detects mmrl object`() {
        assertEquals(RepoFormat.MMRL, RepoModuleParser.sniffFormat(mmrlJson))
    }

    @Test
    fun `sniffFormat tolerates BOM and leading whitespace`() {
        assertEquals(RepoFormat.KERNELSU, RepoModuleParser.sniffFormat("\uFEFF\n " + ksuJson))
    }

    @Test
    fun `sniffFormat rejects unknown content`() {
        assertNull(RepoModuleParser.sniffFormat("<html>404</html>"))
        assertNull(RepoModuleParser.sniffFormat("{\"foo\": 1}"))
        assertNull(RepoModuleParser.sniffFormat(""))
    }

    @Test
    fun `parseKsu maps module fields`() {
        val modules = RepoModuleParser.parseKsu(ksuJson, "src1", "Src1")
        assertEquals(1, modules.size)
        val module = modules[0]
        assertEquals("zygisk_assistant", module.moduleId)
        assertEquals("Zygisk Assistant", module.moduleName)
        assertEquals("jijingg", module.authors)
        assertTrue(module.zygisk)
        assertEquals(42, module.stargazerCount)
        assertEquals("v2.2", module.latestRelease)
        assertEquals(202L, module.latestVersionCode)
        assertEquals("src1", module.sourceId)
        assertEquals("Src1", module.sourceName)
        assertNotNull(module.latestAsset)
        assertEquals("ZygiskAssistant-v2.2.zip", module.latestAsset?.name)
    }

    @Test
    fun `parseKsu skips entries without moduleId`() {
        val modules = RepoModuleParser.parseKsu("""[{"moduleName": "broken"}, {"moduleId": "ok"}]""", "", "")
        assertEquals(listOf("ok"), modules.map { it.moduleId })
    }

    @Test
    fun `parseMmrl maps module fields and sorts versions`() {
        val modules = RepoModuleParser.parseMmrl(mmrlJson, "src1", "Src1")
        assertEquals(2, modules.size)
        val dict = modules[0]
        assertEquals("CustomPinyinDictionary", dict.moduleId)
        assertEquals("wuhgit", dict.authors)
        assertEquals(1, dict.authorList.size)
        assertEquals("Gboard custom pinyin dictionary", dict.summary)
        assertEquals("src1", dict.sourceId)
        assertEquals("Src1", dict.sourceName)

        val mmrl = dict.mmrl
        assertNotNull(mmrl)
        assertEquals("https://raw.githubusercontent.com/wuhgit/CustomPinyinDictionary/main/README.md", mmrl!!.readmeUrl)
        assertEquals(2, mmrl.versions.size)
        // highest versionCode first, string versionCode must be tolerated
        assertEquals(20260101L, mmrl.versions[0].versionCode)
        assertEquals("https://example.com/dict_v20260101.zip", mmrl.versions[0].zipUrl)

        assertEquals("v20260101", dict.latestRelease)
        assertEquals(20260101L, dict.latestVersionCode)
        assertEquals("https://example.com/dict_v20260101.zip", dict.latestAsset?.downloadUrl)
        assertEquals(30270378L, dict.latestAsset?.size)
        // epoch seconds -> ISO-8601 UTC
        assertEquals("2025-12-31T23:19:52Z", dict.latestReleaseTime)
        assertEquals(dict.latestReleaseTime, mmrl.versions[0].time)
    }

    @Test
    fun `parseMmrl tolerates modules without versions`() {
        val modules = RepoModuleParser.parseMmrl(mmrlJson, "src1", "Src1")
        val noVersions = modules.first { it.moduleId == "no_versions_module" }
        assertTrue(noVersions.mmrl?.versions.isNullOrEmpty())
        assertEquals("", noVersions.latestRelease)
        assertEquals(0L, noVersions.latestVersionCode)
        assertNull(noVersions.latestAsset)
    }

    @Test
    fun `parseMmrl returns empty list when modules key missing`() {
        assertTrue(RepoModuleParser.parseMmrl("""{"id": "x"}""", "", "").isEmpty())
    }

    @Test
    fun `parse throws on unrecognized format`() {
        try {
            RepoModuleParser.parse("not json", "", "")
            throw AssertionError("expected exception")
        } catch (expected: IllegalArgumentException) {
        }
    }

    @Test
    fun `parse throws on malformed json`() {
        try {
            RepoModuleParser.parse("[broken", "", "")
            throw AssertionError("expected exception")
        } catch (expected: JSONException) {
        }
    }

    @Test
    fun `mmrl module without id is skipped`() {
        val json = """{"modules": [{"name": "no id"}, {"id": "ok"}]}"""
        val modules = RepoModuleParser.parseMmrl(json, "", "")
        assertEquals(listOf("ok"), modules.map { it.moduleId })
    }

    @Test
    fun `parse dispatches by sniffed format`() {
        assertEquals("zygisk_assistant", RepoModuleParser.parse(ksuJson, "s", "S").single().moduleId)
        assertEquals("CustomPinyinDictionary", RepoModuleParser.parse(mmrlJson, "s", "S").first().moduleId)
        assertFalse(RepoModuleParser.parse(mmrlJson, "s", "S").first().zygisk)
    }

    @Test
    fun `parseMmrl leaves assets already on the index host without a fallback`() {
        val module = RepoModuleParser.parseMmrl(
            mmrlJson, "s", "S", "https://example.com/json/modules.json",
        ).first()

        assertTrue(module.mmrl!!.versions.all { it.zipUrlFallback == null })
    }

    @Test
    fun `parseMmrl falls back to the index host when the asset host differs`() {
        val json = """
            {"modules": [{"id": "copg", "versions": [
              {"version": "1.0", "zipUrl": "https://old.example.com/repo/modules/copg/1.0.zip"}
            ]}]}
        """.trimIndent()

        val module = RepoModuleParser.parseMmrl(
            json, "s", "S", "https://new.example.com/repo/json/modules.json",
        ).single()

        assertEquals(
            "https://new.example.com/repo/modules/copg/1.0.zip",
            module.mmrl!!.versions.single().zipUrlFallback,
        )
    }

    private val registryIndexJson = """
        {
          "schemaVersion": 1,
          "buildId": "70d4a7bbd921a57b",
          "repoCommit": "a2ad01cee2a31469b4ce1676b7be097e53dc8be0",
          "generatedAt": "2026-10-12T09:19:47.073Z",
          "modules": "api/v1/modules.json"
        }
    """.trimIndent()

    private val registryModulesJson = """
        {
          "schemaVersion": 1,
          "buildId": "70d4a7bbd921a57b",
          "modules": [
            {
              "id": "CertBridge",
              "name": "证书桥",
              "summary": "把 PEM 注入系统信任库",
              "authors": [{"name": "许小墨", "url": "https://github.com/Eikeitsu"}],
              "tags": ["system", "security"],
              "metamodule": false,
              "icon": "assets/2ed88922ec2dff12.png",
              "stable": {
                "version": "v5.1.2",
                "versionCode": 50107,
                "publishedAt": "2026-10-11T16:18:27Z"
              },
              "stars": 26,
              "starsRepository": "Eikeitsu/CertBridge",
              "detail": "api/v1/modules/CertBridge.json",
              "updatedAt": "2026-10-11T17:51:03.255Z"
            },
            {
              "id": "no_detail_module",
              "name": "No Detail",
              "stable": {"version": "v1", "versionCode": 1}
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `sniffFormat tells a registry list from an mmrl one`() {
        assertEquals(RepoFormat.KERNELSU_V1, RepoModuleParser.sniffFormat(registryModulesJson))
        // The pointer document names its module list instead of carrying it.
        assertEquals(RepoFormat.KERNELSU_V1, RepoModuleParser.sniffFormat(registryIndexJson))
        assertEquals(RepoFormat.MMRL, RepoModuleParser.sniffFormat(mmrlJson))
    }

    @Test
    fun `registryIndexPointer reads the pointer document and only that`() {
        assertEquals("api/v1/modules.json", RepoModuleParser.registryIndexPointer(registryIndexJson))
        // An MMRL index has a modules array; optString would hand back its serialized text.
        assertNull(RepoModuleParser.registryIndexPointer(mmrlJson))
        assertNull(RepoModuleParser.registryIndexPointer(registryModulesJson))
    }

    @Test
    fun `parseKsuV1 maps module fields and locates the detail document`() {
        val modules = RepoModuleParser.parseKsuV1(
            registryModulesJson, "src1", "Src1", "https://modules.kernelsu.org/api/v1/modules.json",
        )
        assertEquals(2, modules.size)
        val cert = modules[0]
        assertEquals("CertBridge", cert.moduleId)
        assertEquals("证书桥", cert.moduleName)
        assertEquals("许小墨", cert.authors)
        assertEquals("https://github.com/Eikeitsu", cert.authorList.single().link)
        assertEquals("把 PEM 注入系统信任库", cert.summary)
        assertEquals("v5.1.2", cert.latestRelease)
        assertEquals(50107L, cert.latestVersionCode)
        assertEquals("2026-10-11T16:18:27Z", cert.latestReleaseTime)
        assertEquals(26, cert.stargazerCount)
        assertEquals("src1", cert.sourceId)

        // The index carries no download address: it is read per module, when one is opened.
        assertNull(cert.latestAsset)
        assertNull(cert.mmrl)

        // `detail` is relative to the site root, not to the document that named it.
        assertEquals("https://modules.kernelsu.org/", cert.registry?.baseUrl)
        assertEquals("https://modules.kernelsu.org/api/v1/modules/CertBridge.json", cert.registry?.detailUrl)
    }

    @Test
    fun `parseKsuV1 keeps a module whose detail path is missing`() {
        val modules = RepoModuleParser.parseKsuV1(
            registryModulesJson, "", "", "https://modules.kernelsu.org/api/v1/modules.json",
        )
        val noDetail = modules.first { it.moduleId == "no_detail_module" }
        assertNull(noDetail.registry)
        assertEquals("v1", noDetail.latestRelease)
    }

    @Test
    fun `parseKsuV1 skips entries without an id`() {
        val json = """{"schemaVersion": 1, "modules": [{"name": "broken"}, {"id": "ok"}]}"""
        assertEquals(listOf("ok"), RepoModuleParser.parseKsuV1(json, "", "").map { it.moduleId })
    }

    @Test
    fun `parseKsuV1 tolerates a registry served from a non-default port`() {
        val modules = RepoModuleParser.parseKsuV1(
            registryModulesJson, "", "", "http://registry.example.com:8080/api/v1/modules.json",
        )
        assertEquals(
            "http://registry.example.com:8080/api/v1/modules/CertBridge.json",
            modules.first().registry?.detailUrl,
        )
    }

    @Test
    fun `parse dispatches a registry list to the v1 parser`() {
        val modules = RepoModuleParser.parse(registryModulesJson, "s", "S", "https://modules.kernelsu.org/api/v1/modules.json")
        assertEquals("CertBridge", modules.first().moduleId)
        assertNotNull(modules.first().registry)
        // MMRL parsing would have found no versions and still claimed the module; the formats are
        // told apart so that a registry module is not mistaken for an MMRL one with nothing in it.
        assertNull(modules.first().mmrl)
    }

    @Test
    fun `registryBaseUrl keeps the site root and rejects what has none`() {
        assertEquals("https://modules.kernelsu.org/", registryBaseUrl("https://modules.kernelsu.org/api/v1/index.json"))
        assertEquals("https://example.com:8443/", registryBaseUrl("https://example.com:8443/x/y.json"))
        assertNull(registryBaseUrl("not a url"))
        assertNull(registryBaseUrl("file:///tmp/modules.json"))
    }

    @Test
    fun `registryDocumentUrl resolves against the site root`() {
        assertEquals(
            "https://modules.kernelsu.org/api/v1/modules/CertBridge.json",
            registryDocumentUrl("https://modules.kernelsu.org/", "api/v1/modules/CertBridge.json"),
        )
        assertEquals(
            "https://modules.kernelsu.org/api/v1/modules/CertBridge.json",
            registryDocumentUrl("https://modules.kernelsu.org/", "/api/v1/modules/CertBridge.json"),
        )
    }
    // ── 源码仓库字段（服务端写入索引） ──────────────────────────────────────

    private val repoJson = """
        {
          "modules": [
            {
              "id": "repo_module",
              "name": "Repo Module",
              "author": "me",
              "repoUrl": "https://GitHub.com/Owner/Repo.git/",
              "repoVerified": true,
              "repoCheckedAt": 1700000000,
              "repoLicense": "MIT",
              "versions": [
                {
                  "version": "v1.2.3",
                  "versionCode": 12,
                  "timestamp": 1700000000,
                  "zipUrl": "https://example.com/v1.2.3.zip",
                  "repoTag": "v1.2.3",
                  "repoDigest": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "auto": true,
                  "autoVia": "repo"
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `mmrl carries repo evidence through`() {
        val module = RepoModuleParser.parseMmrl(repoJson, "src", "Src").single()
        // 地址归一化在解析层完成：下游只认这一种形状。
        assertEquals("https://github.com/Owner/Repo", module.repoUrl)
        assertTrue(module.repoVerified)
        // 索引给的是秒，UI 用毫秒：解析时统一乘开，别让两边各自猜。
        assertEquals(1_700_000_000_000L, module.repoCheckedAt)
        assertEquals("MIT", module.repoLicense)
        // 最新一版带 repoTag，所以覆盖最新版。
        assertTrue(module.repoCoversLatest)

        val version = module.mmrl!!.versions.single()
        assertEquals("v1.2.3", version.repoTag)
        assertEquals("sha256:" + "a".repeat(64), version.repoDigest)
        assertTrue(version.auto)
        assertEquals("repo", version.autoVia)
    }

    @Test
    fun `repo fields are optional`() {
        val body = """
            {"modules": [{"id": "plain", "name": "Plain", "versions": [
              {"version": "v1", "versionCode": 1, "zipUrl": "https://example.com/v1.zip"}
            ]}]}
        """.trimIndent()
        val module = RepoModuleParser.parseMmrl(body, "", "").single()
        assertEquals("", module.repoUrl)
        assertFalse(module.repoVerified)
        assertEquals(0L, module.repoCheckedAt)
        assertEquals("", module.repoLicense)
        // 派生：最新一版没有 repoTag，就是没覆盖最新版。
        assertFalse(module.repoCoversLatest)
        val version = module.mmrl!!.versions.single()
        assertEquals("", version.repoTag)
        assertEquals("", version.repoDigest)
        assertFalse(version.auto)
        assertEquals("", version.autoVia)
    }

    @Test
    fun `malformed repo values are dropped instead of stored`() {
        val body = """
            {"modules": [{
              "id": "broken", "name": "Broken",
              "repoUrl": "https://gitlab.com/owner/repo",
              "repoVerified": "yes",
              "repoCheckedAt": "yesterday",
              "versions": [{
                "version": "v1", "versionCode": 1, "zipUrl": "https://example.com/v1.zip",
                "repoTag": "v1",
                "repoDigest": "deadbeef",
                "auto": true,
                "autoVia": "manual"
              }]
            }]}
        """.trimIndent()
        val module = RepoModuleParser.parseMmrl(body, "", "").single()
        // 一个坏字段不该让整条模块消失，也不该把非法值原样带进 UI。
        assertEquals("broken", module.moduleId)
        assertEquals("", module.repoUrl)
        assertFalse(module.repoVerified)
        assertEquals(0L, module.repoCheckedAt)
        val version = module.mmrl!!.versions.single()
        assertEquals("", version.repoDigest)
        // autoVia 只认两个通道；认不得就当没这个字段。
        assertEquals("", version.autoVia)
    }

    @Test
    fun `author links are normalized during parse`() {
        val body = """
            {"modules": [{
              "id": "with_authors", "name": "With Authors", "author": "Alice",
              "authorCoolapkUrl": "www.coolapk.com/u/123456",
              "authorGithubUrl": "github.com/OctoCat",
              "versions": [{"version": "v1", "versionCode": 1, "zipUrl": "https://example.com/v1.zip"}]
            }]}
        """.trimIndent()
        val module = RepoModuleParser.parseMmrl(body, "src", "Src").single()
        // 归一化在解析层做完，下游只认规范形状；GitHub login 保留原始大小写。
        assertEquals("https://www.coolapk.com/u/123456", module.authorCoolapkUrl)
        assertEquals("https://github.com/OctoCat", module.authorGithubUrl)
        // MMRL 仍然只有单作者，作者主页不改变作者列表的形状。
        assertEquals("Alice", module.authorList.single().name)
    }

    @Test
    fun `malformed author links are dropped instead of stored`() {
        val body = """
            {"modules": [{
              "id": "bad_authors", "name": "Bad Authors", "author": "Alice",
              "authorCoolapkUrl": "https://www.coolapk.com/feed/1",
              "authorGithubUrl": "https://github.com/octocat/repo",
              "versions": [{"version": "v1", "versionCode": 1, "zipUrl": "https://example.com/v1.zip"}]
            }]}
        """.trimIndent()
        val module = RepoModuleParser.parseMmrl(body, "", "").single()
        // 作者主页只是附加信息：坏值丢掉，模块本身仍然照常出现。
        assertEquals("bad_authors", module.moduleId)
        assertEquals("", module.authorCoolapkUrl)
        assertEquals("", module.authorGithubUrl)
        assertEquals("Alice", module.authorList.single().name)
    }
}
