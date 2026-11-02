package com.sukisu.ultra.ai

import androidx.compose.runtime.Composable
import com.sukisu.ultra.ai.api.AiFeatureProvider
import com.sukisu.ultra.ai.api.AiHost
import com.sukisu.ultra.ai.api.AiNavigationTarget

/**
 * Reflectively loaded by [com.sukisu.ultra.ai.api.AiFeatureBridge]. Keeping the
 * name and public no-argument constructor stable is part of the ABI.
 */
class AiFeatureProviderImpl : AiFeatureProvider {

    override fun install(host: AiHost) {
        AiAssistant.install(host)
    }

    override fun chatScreen(
        onClose: () -> Unit,
        onOpenSettings: () -> Unit,
        onNavigate: (AiNavigationTarget) -> Unit,
    ): @Composable () -> Unit = {
        AiChatScreen(
            onClose = onClose,
            onOpenSettings = onOpenSettings,
            onNavigate = onNavigate,
        )
    }

    override fun settingsScreen(
        onBack: () -> Unit,
    ): @Composable () -> Unit = {
        AiSettingsScreen(onBack = onBack)
    }

    override fun homeCard(onOpenChat: () -> Unit): @Composable () -> Unit = {
        AiHomeCardMaterial(onOpenChat = onOpenChat)
    }
}
