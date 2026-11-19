package me.luiscruzandresgomezrodolfohernandez.geolux.ui.model3d

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.ToneMapper
import com.google.android.filament.View

/**
 * Lo que el teléfono puede dibujar, para preparar el modelo 3D con la mejor calidad
 * posible sin pasarse: el tamaño máximo de textura del GPU y la memoria que se
 * puede dedicar a las texturas.
 */
object GpuLimits {
    /** Si no se puede preguntar al GPU: lo que admite cualquier GPU de OpenGL ES 3.0. */
    private const val SAFE_MAX_TEXTURE = 2048

    /** `GL_MAX_TEXTURE_SIZE` con un contexto EGL de 1×1 que se crea y se destruye enseguida. */
    fun maxTextureSize(): Int = runCatching {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return SAFE_MAX_TEXTURE
        try {
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            val attributes = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, 0x40 /* EGL_OPENGL_ES3_BIT */, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE)
            if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) || count[0] == 0) return SAFE_MAX_TEXTURE
            val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
            val surface = EGL14.eglCreatePbufferSurface(display, configs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            try {
                EGL14.eglMakeCurrent(display, surface, surface, context)
                val size = IntArray(1)
                GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, size, 0)
                size[0].takeIf { it >= 1024 } ?: SAFE_MAX_TEXTURE
            } finally {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(display, surface)
                EGL14.eglDestroyContext(display, context)
            }
        } finally {
            EGL14.eglTerminate(display)
        }
    }.getOrDefault(SAFE_MAX_TEXTURE)

    /**
     * Memoria para las texturas del modelo: una quinta parte de la RAM del teléfono
     * (en Android el GPU comparte la RAM), sin pasar de 1,5 GB. Un teléfono de 4 GB
     * dedica unos 800 MB.
     */
    fun textureBudgetBytes(context: Context): Long {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return minOf(info.totalMem / 5, 1536L * 1024 * 1024)
    }
}

/**
 * Render fiel para un modelo de fotogrametría (ODM, materiales *unlit*: la luz ya está
 * en las fotos), como el visor de Desktop, que pinta el color de la textura tal cual.
 * SceneView aplica por defecto tone mapping Filmic, bloom, SSAO y FXAA: cambian los
 * colores y suavizan la foto. Aquí: tone mapping lineal, sin bloom ni SSAO, y MSAA 4×
 * en vez de FXAA (bordes limpios sin desenfocar la textura).
 */
fun View.applyFaithfulRendering(engine: Engine) {
    colorGrading = ColorGrading.Builder().toneMapper(ToneMapper.Linear()).build(engine)
    bloomOptions = bloomOptions.apply { enabled = false }
    ambientOcclusionOptions = ambientOcclusionOptions.apply { enabled = false }
    dynamicResolutionOptions = dynamicResolutionOptions.apply { enabled = false }
    antiAliasing = View.AntiAliasing.NONE
    multiSampleAntiAliasingOptions = multiSampleAntiAliasingOptions.apply {
        enabled = true
        sampleCount = 4
    }
}
