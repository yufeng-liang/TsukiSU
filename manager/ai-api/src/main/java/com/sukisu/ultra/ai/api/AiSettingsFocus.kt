package com.sukisu.ultra.ai.api

/** 设置页五个落点。 */
enum class AiSettingsTarget { Model, Search, ReadScope, WriteScope, Behavior }

/** 一次性信使：聊天页与助手工具写，设置页读一次就清空。 */
object AiSettingsFocus {
    private var pending: AiSettingsTarget? = null
    fun request(target: AiSettingsTarget) { pending = target }
    fun consume(): AiSettingsTarget? = pending.also { pending = null }
    fun clear() { pending = null }
}
