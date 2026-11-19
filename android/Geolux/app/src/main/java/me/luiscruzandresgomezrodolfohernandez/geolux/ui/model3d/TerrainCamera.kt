package me.luiscruzandresgomezrodolfohernandez.geolux.ui.model3d

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.lookAt
import io.github.sceneview.gesture.CameraGestureDetector
import io.github.sceneview.math.Transform
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.tan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Cámara de un terreno: mira a un punto del piso (el plano horizontal y = 0, donde
 * SceneView deja el centro del modelo) y gira alrededor del eje vertical que pasa por
 * él. La inclinación está limitada, así que nunca se ve la finca desde abajo ni de
 * canto. El modelo mide 1 unidad (`scaleToUnits = 1`).
 *
 * Solo hace cuentas, sin Filament: se prueba en la JVM ([TerrainViewTest]).
 */
internal class TerrainView {
    /** Punto del piso al que se mira. */
    var targetX = 0f
        private set
    var targetZ = 0f
        private set

    /** Giro alrededor del eje vertical, en radianes. Con 0 la cámara está en +z. */
    var yaw = HOME_YAW
        private set

    /** Ángulo sobre el piso, en radianes, entre [MIN_PITCH] y [MAX_PITCH]. */
    var pitch = HOME_PITCH
        private set

    var distance = HOME_DISTANCE
        private set

    fun rotate(radians: Float) {
        yaw = ((yaw + radians) % TWO_PI + TWO_PI) % TWO_PI
    }

    fun tilt(radians: Float) {
        pitch = (pitch + radians).coerceIn(MIN_PITCH, MAX_PITCH)
    }

    /** Acerca ([factor] > 1) o aleja ([factor] < 1). */
    fun zoom(factor: Float) {
        if (factor > 0f) distance = (distance / factor).coerceIn(MIN_DISTANCE, MAX_DISTANCE)
    }

    /**
     * Mueve el punto de mira sobre el piso, relativo a hacia dónde se mira:
     * [forward] > 0 avanza y [right] > 0 va a la derecha. No sale de la finca.
     */
    fun move(right: Float, forward: Float) {
        // Hacia delante es de la cámara al punto, en horizontal: (−sin yaw, −cos yaw).
        val fx = -sin(yaw)
        val fz = -cos(yaw)
        // A la derecha: hacia delante girado 90° en el piso.
        val rx = -fz
        val rz = fx
        targetX = (targetX + rx * right + fx * forward).coerceIn(-TARGET_LIMIT, TARGET_LIMIT)
        targetZ = (targetZ + rz * right + fz * forward).coerceIn(-TARGET_LIMIT, TARGET_LIMIT)
    }

    /** Ancho/alto del visor; null hasta conocerlo (entonces se usa [HOME_DISTANCE]). */
    var aspect: Float? = null
        private set

    /**
     * Distancia a la que la finca (1 unidad de lado como mucho) llena el visor con un margen,
     * vista con la inclinación actual. Con la pantalla en horizontal limita la altura y el
     * modelo se acerca mucho más que con la distancia fija de antes, pensada para vertical.
     */
    fun fitDistance(aspect: Float? = this.aspect): Float {
        if (aspect == null) return HOME_DISTANCE
        val tanHalfV = tan(VERTICAL_FOV / 2)
        val tanHalfH = tanHalfV * aspect
        val byWidth = FIT_HALF_SIZE / tanHalfH
        // La mitad cercana de la finca se ve más grande por la perspectiva: un margen extra en alto.
        val byHeight = (FIT_HALF_SIZE * sin(pitch) + FIT_RELIEF) * NEAR_SIDE / tanHalfV
        return maxOf(byWidth, byHeight).coerceIn(MIN_DISTANCE, MAX_DISTANCE)
    }

    /**
     * El visor cambió de forma (al girar el teléfono o entrar en pantalla completa): se
     * conservan el giro, la inclinación y el punto de mira, y la distancia se escala para
     * que el modelo ocupe en la pantalla nueva lo mismo que ocupaba en la anterior.
     */
    fun setAspect(value: Float) {
        if (value <= 0f || !value.isFinite() || value == aspect) return
        val before = fitDistance()
        aspect = value
        distance = (distance * fitDistance() / before).coerceIn(MIN_DISTANCE, MAX_DISTANCE)
    }

    /** Ajustar a la pantalla: centra la finca y la deja llenando el visor, sin cambiar el giro. */
    fun fit() {
        targetX = 0f
        targetZ = 0f
        distance = fitDistance()
    }

    fun reset() {
        targetX = 0f
        targetZ = 0f
        yaw = HOME_YAW
        pitch = HOME_PITCH
        distance = fitDistance()
    }

    fun target() = Float3(targetX, 0f, targetZ)

    fun eye(): Float3 {
        val horizontal = distance * cos(pitch)
        return Float3(
            targetX + horizontal * sin(yaw),
            distance * sin(pitch),
            targetZ + horizontal * cos(yaw),
        )
    }

    companion object {
        const val MIN_PITCH = (15 * PI / 180).toFloat()
        const val MAX_PITCH = (85 * PI / 180).toFloat()
        const val MIN_DISTANCE = 0.25f
        const val MAX_DISTANCE = 4f

        /** El punto de mira no pasa del borde de la finca (media unidad desde el centro). */
        const val TARGET_LIMIT = 0.5f

        /** Vista inicial: como la de antes (elevada unos 40°, a algo más de 2 unidades). */
        const val HOME_YAW = 0f
        const val HOME_PITCH = (40 * PI / 180).toFloat()
        const val HOME_DISTANCE = 2.4f

        private const val TWO_PI = (2 * PI).toFloat()

        /** Campo de visión vertical de la cámara de SceneView (lente de 28 mm, 24 mm de sensor). */
        val VERTICAL_FOV = (2 * atan(12.0 / 28.0)).toFloat()

        /** Media finca más un 10 % de margen, y un poco de relieve por encima del piso. */
        private const val FIT_HALF_SIZE = 0.55f
        private const val FIT_RELIEF = 0.08f
        private const val NEAR_SIDE = 1.3f
    }
}

/**
 * Gestos de SceneView sobre una [TerrainView]:
 * - un dedo: en horizontal gira alrededor del eje vertical; en vertical inclina;
 * - dos dedos arrastrando: mueven el punto de mira sobre el piso;
 * - pellizcar: acerca y aleja.
 *
 * Los botones del visor usan la misma [view]. SceneView pide [getTransform] en cada
 * fotograma, en el hilo principal, como los toques y los botones.
 */
internal class TerrainCamera(val view: TerrainView = TerrainView()) : CameraGestureDetector.CameraManipulator {
    private var width = 1
    private var height = 1
    private var lastX = 0
    private var lastY = 0
    private var strafe = false
    private var grabbing = false

    override fun setViewport(width: Int, height: Int) {
        this.width = max(width, 1)
        this.height = max(height, 1)
        view.setAspect(this.width.toFloat() / this.height)
    }

    override fun getTransform(): Transform = lookAt(eye = view.eye(), target = view.target(), up = Float3(y = 1f))

    // x e y llegan en píxeles con y hacia arriba (SceneView invierte la del toque).
    override fun grabBegin(x: Int, y: Int, strafe: Boolean) {
        lastX = x
        lastY = y
        this.strafe = strafe
        grabbing = true
    }

    override fun grabUpdate(x: Int, y: Int) {
        if (!grabbing) return
        val dx = (x - lastX).toFloat()
        val dy = (y - lastY).toFloat()
        lastX = x
        lastY = y
        if (strafe) {
            // Lo que está bajo los dedos los sigue, como en un mapa: arrastrar a la derecha
            // lleva la mira a la izquierda y subir los dedos la trae hacia la cámara.
            val perPixel = view.distance / height
            view.move(right = -dx * perPixel, forward = -dy * perPixel)
        } else {
            // Un ancho de pantalla, una vuelta entera; arrastrar hacia arriba baja la vista.
            view.rotate(-dx / width * FULL_TURN)
            view.tilt(-dy / height * HALF_TURN)
        }
    }

    override fun grabEnd() {
        grabbing = false
    }

    override fun scrollBegin(x: Int, y: Int, separation: Float) {}

    override fun scrollUpdate(x: Int, y: Int, prevSeparation: Float, currSeparation: Float) {
        if (prevSeparation > 0f && currSeparation > 0f) view.zoom(currSeparation / prevSeparation)
    }

    override fun scrollEnd() {}

    override fun update(deltaTime: Float) {}

    private companion object {
        const val FULL_TURN = (2 * PI).toFloat()
        const val HALF_TURN = PI.toFloat()
    }
}
