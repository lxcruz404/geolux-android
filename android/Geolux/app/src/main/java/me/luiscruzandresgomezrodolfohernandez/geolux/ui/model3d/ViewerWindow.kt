package me.luiscruzandresgomezrodolfohernandez.geolux.ui.model3d

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Mientras se ve la pestaña 3D, la pantalla sigue al sensor aunque el teléfono tenga el giro
 * automático bloqueado (como un reproductor de vídeo): girarlo 90° pone el visor en
 * horizontal. Solo en esta pestaña; al salir se devuelve la orientación que tenía la app.
 */
@Composable
internal fun ViewerOrientationEffect(active: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity, active) {
        if (!active) return@DisposableEffect onDispose { }
        val before = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        onDispose { activity.requestedOrientation = before }
    }
}

/**
 * Pantalla completa del visor: oculta la barra de estado y la de navegación del sistema.
 * Deslizar desde el borde las muestra un momento (los gestos del sistema siguen funcionando).
 */
@Composable
internal fun ImmersiveSystemBarsEffect(immersive: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity, immersive) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (immersive) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { if (immersive) controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
