package me.luiscruzandresgomezrodolfohernandez.geolux.ui.model3d

import dev.romainguy.kotlin.math.Float3
import kotlin.math.PI
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cámara del visor 3D: gira sobre el piso y nunca lo atraviesa. */
class TerrainViewTest {
    private val eps = 1e-4f

    // Las operaciones de kotlin-math son inline y se compilaron para JVM 21; las pruebas, para 11.
    private fun distance(a: Float3, b: Float3): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }

    @Test
    fun ningunaCombinacionDeGestosBajaDelPiso() {
        val camera = TerrainCamera().apply { setViewport(1080, 1920) }
        val random = Random(7)
        repeat(2_000) {
            when (random.nextInt(4)) {
                0 -> camera.view.rotate(random.nextFloat() * 10 - 5)
                1 -> camera.view.tilt(random.nextFloat() * 10 - 5)
                2 -> camera.view.zoom(random.nextFloat() * 4 + 0.1f)
                else -> camera.view.move(random.nextFloat() * 4 - 2, random.nextFloat() * 4 - 2)
            }
            val view = camera.view
            assertTrue(view.eye().y > 0f)
            assertTrue(view.pitch in TerrainView.MIN_PITCH..TerrainView.MAX_PITCH)
            assertTrue(view.distance in TerrainView.MIN_DISTANCE..TerrainView.MAX_DISTANCE)
            assertTrue(kotlin.math.abs(view.targetX) <= TerrainView.TARGET_LIMIT)
            assertTrue(kotlin.math.abs(view.targetZ) <= TerrainView.TARGET_LIMIT)
        }
    }

    @Test
    fun arrastrarHaciaAbajoNoPasaDeLaVistaCenital() {
        val camera = TerrainCamera().apply { setViewport(1000, 1000) }
        camera.grabBegin(500, 900, strafe = false)
        camera.grabUpdate(500, -5_000) // muy por debajo de la pantalla
        camera.grabEnd()
        assertEquals(TerrainView.MAX_PITCH, camera.view.pitch, eps)
        camera.grabBegin(500, 0, strafe = false)
        camera.grabUpdate(500, 9_000)
        assertEquals(TerrainView.MIN_PITCH, camera.view.pitch, eps)
    }

    @Test
    fun girarEsAlrededorDelEjeVerticalDelPuntoDeMira() {
        val view = TerrainView()
        view.move(right = 0.2f, forward = 0.1f)
        val target = view.target()
        val before = view.eye()
        repeat(12) {
            view.rotate((PI / 6).toFloat())
            val eye = view.eye()
            // Misma altura y misma distancia al punto de mira, que no se mueve.
            assertEquals(before.y, eye.y, eps)
            assertEquals(distance(before, target), distance(eye, target), eps)
            assertEquals(target, view.target())
        }
        // Una vuelta entera vuelve al mismo sitio.
        assertEquals(before.x, view.eye().x, eps)
        assertEquals(before.z, view.eye().z, eps)
    }

    @Test
    fun avanzarVaHaciaDondeSeMira() {
        val view = TerrainView()
        // Vista inicial: la cámara está en +z y mira hacia −z.
        view.move(right = 0f, forward = 0.1f)
        assertEquals(-0.1f, view.targetZ, eps)
        assertEquals(0f, view.targetX, eps)
        view.move(right = 0.1f, forward = 0f)
        assertEquals(0.1f, view.targetX, eps)

        // Girada un cuarto de vuelta, la cámara está en +x y avanzar va hacia −x.
        view.reset()
        view.rotate((PI / 2).toFloat())
        assertTrue(view.eye().x > 1f)
        view.move(right = 0f, forward = 0.1f)
        assertEquals(-0.1f, view.targetX, eps)
        assertEquals(0f, view.targetZ, eps)
    }

    @Test
    fun conDosDedosLoQueEstaBajoLosDedosLosSigue() {
        val camera = TerrainCamera().apply { setViewport(1000, 1000) }
        // A la derecha: la mira va a la izquierda (−x en la vista inicial).
        camera.grabBegin(100, 500, strafe = true)
        camera.grabUpdate(300, 500)
        camera.grabEnd()
        assertTrue(camera.view.targetX < 0f)
        // Hacia arriba: la mira se acerca a la cámara (+z).
        camera.grabBegin(500, 100, strafe = true)
        camera.grabUpdate(500, 300)
        camera.grabEnd()
        assertTrue(camera.view.targetZ > 0f)
    }

    @Test
    fun pellizcarAcercaYAleja() {
        val camera = TerrainCamera()
        val start = camera.view.distance
        camera.scrollUpdate(0, 0, prevSeparation = 100f, currSeparation = 200f)
        assertEquals(start / 2, camera.view.distance, eps)
        camera.scrollUpdate(0, 0, prevSeparation = 200f, currSeparation = 100f)
        assertEquals(start, camera.view.distance, eps)
    }

    @Test
    fun recentrarVuelveALaVistaInicial() {
        val camera = TerrainCamera()
        val home = camera.getTransform()
        camera.view.rotate(1f)
        camera.view.tilt(0.5f)
        camera.view.zoom(2f)
        camera.view.move(0.3f, -0.2f)
        assertTrue(home != camera.getTransform())
        camera.view.reset()
        assertEquals(home, camera.getTransform())
    }

    @Test
    fun enHorizontalElModeloSeAcercaYLlenaLaPantalla() {
        val view = TerrainView()
        assertEquals(TerrainView.HOME_DISTANCE, view.fitDistance(), eps) // sin visor aún
        val portrait = view.fitDistance(0.6f)
        val landscape = view.fitDistance(2.1f)
        // En horizontal limita la altura: el modelo queda mucho más cerca (más grande).
        assertTrue(landscape < portrait * 0.7f)
        assertTrue(landscape < TerrainView.HOME_DISTANCE * 0.6f)
        // Ajustar centra y aplica esa distancia sin tocar el giro.
        view.setAspect(2.1f)
        view.rotate(1f)
        view.move(0.2f, 0.1f)
        view.zoom(0.5f)
        view.fit()
        assertEquals(0f, view.targetX, eps)
        assertEquals(landscape, view.distance, eps)
        assertEquals(1f, view.yaw, eps)
    }

    @Test
    fun alGirarElTelefonoSeConservaLaVista() {
        val view = TerrainView()
        view.setAspect(0.6f)
        view.rotate(0.7f)
        view.tilt(0.2f)
        view.move(0.1f, 0.1f)
        view.zoom(1.5f)
        val yaw = view.yaw
        val pitch = view.pitch
        val tx = view.targetX
        val ratio = view.distance / view.fitDistance()
        view.setAspect(2.1f)
        // Mismo giro, inclinación y punto de mira; la distancia, proporcional a la pantalla nueva.
        assertEquals(yaw, view.yaw, eps)
        assertEquals(pitch, view.pitch, eps)
        assertEquals(tx, view.targetX, eps)
        assertEquals(ratio, view.distance / view.fitDistance(), 1e-3f)
        view.setAspect(0.6f)
        assertEquals(ratio, view.distance / view.fitDistance(), 1e-3f)
    }
}

/** El terreno queda sobre el piso venga con Z o con Y hacia arriba. */
class GroundRotationTest {
    @Test
    fun conZHaciaArribaSeGiraYConYHaciaArribaNo() {
        // La Giralda (motor): 530 m este, 647 m norte, 157 m de altura → Z arriba.
        assertEquals(-90f, groundRotation(floatArrayOf(265f, 323f, 78f)).x)
        // Fixture .glx de Desktop: plano en XY.
        assertEquals(-90f, groundRotation(floatArrayOf(5f, 5f, 0f)).x)
        // Fixture antiguo finca-demo: plano en XZ, ya con Y arriba.
        assertEquals(0f, groundRotation(floatArrayOf(50f, 0f, 50f)).x)
    }
}
