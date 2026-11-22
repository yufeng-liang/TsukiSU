package com.sukisu.ultra.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sukisu.ultra.Natives

/**
 * 管理器身份是否可用。
 *
 * 内核里「我是不是管理器」不会自己变：只有授权/撤销、或换过管理器的包名才会变。
 * 所以只在回到前台（[Lifecycle.Event.ON_RESUME]）重读一次，既跟得上用户在别处刚做的授权，
 * 又不会每次重组都白读一遍原生方法。
 */
@Composable
fun rememberKsuReady(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var ready by remember { mutableStateOf(Natives.isManager) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) ready = Natives.isManager
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return ready
}

@Composable
fun KsuIsValid(
    content: @Composable () -> Unit
) {
    val isManager = rememberKsuReady()
    val ksuVersion = if (isManager) Natives.version else null

    if (ksuVersion != null) {
        content()
    }
}
