package com.sukisu.ultra.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiWebControllerTest {

    @Test
    fun acceptsOnlyGithubReleaseDownloadUrls() {
        assertTrue(
            AiWebController.isGithubReleaseUrl(
                "https://github.com/owner/repo/releases/download/v1.0/module.zip"
            )
        )
        assertFalse(AiWebController.isGithubReleaseUrl("https://example.com/module.zip"))
        assertFalse(AiWebController.isGithubReleaseUrl("http://github.com/owner/repo/releases/download/v1.0/module.zip"))
    }
}
