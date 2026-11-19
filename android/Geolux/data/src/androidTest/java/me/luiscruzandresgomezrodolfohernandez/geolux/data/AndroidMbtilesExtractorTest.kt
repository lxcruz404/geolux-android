package me.luiscruzandresgomezrodolfohernandez.geolux.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import me.luiscruzandresgomezrodolfohernandez.geolux.core.geolux.GeoluxReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Extractor real con el SQLite de Android sobre el ortomosaico de finca-demo (AND-A6, AND-R4). */
@RunWith(AndroidJUnit4::class)
class AndroidMbtilesExtractorTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val work = File(instrumentation.targetContext.cacheDir, "tiles-test").apply { deleteRecursively(); mkdirs() }

    @Test
    fun fincaDemoTilesBecomeXyzPngFiles() {
        val pkg = File(work, "finca-demo.geolux")
        instrumentation.context.assets.open("finca-demo.geolux").use { input -> pkg.outputStream().use { input.copyTo(it) } }
        val data = GeoluxReader.read(pkg).data!!
        val store = ProjectStore(File(work, "projects"))
        store.install(pkg, data)
        val mbtiles = store.productFile(data.project.id, "ortho-demo")!!

        // Lo que hay en el MBTiles, leído aparte para comparar.
        val expected = SQLiteDatabase.openDatabase(mbtiles.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT zoom_level, tile_column, tile_row, length(tile_data) FROM tiles", null).use { c ->
                generateSequence { if (c.moveToNext()) listOf(c.getInt(0), c.getInt(1), c.getInt(2), c.getInt(3)) else null }.toList()
            }
        }
        assertTrue(expected.isNotEmpty())

        val tiles = store.orthoTiles(data.project.id, "ortho-demo", AndroidMbtilesExtractor)!!
        assertEquals(16, tiles.minZoom)
        assertEquals(16, tiles.maxZoom)
        assertTrue(tiles.urlTemplate.startsWith("file:///") && tiles.urlTemplate.endsWith("/{z}/{x}/{y}.png"))
        val dir = File(tiles.urlTemplate.removePrefix("file://").substringBefore("/{z}"))
        for ((z, x, tmsRow, size) in expected) {
            val file = File(dir, "$z/$x/${xyzRow(z, tmsRow)}.png")
            assertTrue("falta $file", file.isFile)
            assertEquals(size.toLong(), file.length())
        }
        assertTrue(!mbtiles.exists())
    }
}
