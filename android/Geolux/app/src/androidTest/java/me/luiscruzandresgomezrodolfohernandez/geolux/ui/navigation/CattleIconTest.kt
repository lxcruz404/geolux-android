package me.luiscruzandresgomezrodolfohernandez.geolux.ui.navigation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.luiscruzandresgomezrodolfohernandez.geolux.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El icono de la pestaña Ganado se dibuja de verdad en el dispositivo, en los dos estados, con el
 * mismo tamaño que los demás iconos de la barra y teñido por la barra (como ellos).
 */
@RunWith(AndroidJUnit4::class)
class CattleIconTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun render(id: Int, px: Int = 96): Bitmap {
        val drawable = ContextCompat.getDrawable(context, id)!!.mutate()
        drawable.setTint(Color.BLACK)
        drawable.setBounds(0, 0, px, px)
        return Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { drawable.draw(Canvas(it)) }
    }

    private fun opaque(b: Bitmap) = (0 until b.width).sumOf { x -> (0 until b.height).count { y -> Color.alpha(b.getPixel(x, y)) > 128 } }

    @Test
    fun bothStatesDrawAtTheSizeOfTheOtherTabs() {
        val reference = ContextCompat.getDrawable(context, R.drawable.ic_symbol_map)!!
        for (id in listOf(R.drawable.ic_cattle, R.drawable.ic_cattle_filled)) {
            val d = ContextCompat.getDrawable(context, id)!!
            assertEquals(reference.intrinsicWidth, d.intrinsicWidth)
            assertEquals(reference.intrinsicHeight, d.intrinsicHeight)
        }
        val outline = opaque(render(R.drawable.ic_cattle))
        val filled = opaque(render(R.drawable.ic_cattle_filled))
        assertTrue("el icono no dibuja nada", outline > 300)
        assertTrue("la variante seleccionada debe verse rellena ($filled vs $outline)", filled > outline * 13 / 10)
    }

    @Test
    fun theSelectedHeadKeepsItsEyesOpen() {
        // Ojos en (10.25, 11.6) y (13.75, 11.6) de la cuadrícula de 24: huecos reales en la cabeza rellena.
        val b = render(R.drawable.ic_cattle_filled, 240)
        assertTrue(Color.alpha(b.getPixel(102, 116)) < 64)
        assertTrue(Color.alpha(b.getPixel(137, 116)) < 64)
        assertTrue(Color.alpha(b.getPixel(120, 100)) > 192) // entre los ojos: relleno
    }
}
