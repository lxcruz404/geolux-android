package me.luiscruzandresgomezrodolfohernandez.geolux.data

import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Teselas de un ortomosaico listas para una fuente ráster de MapLibre.
 * [urlTemplate] es `file:///…/{z}/{x}/{y}.png` (o `.jpg`), filas en esquema XYZ.
 */
data class TileSource(val urlTemplate: String, val minZoom: Int, val maxZoom: Int)

/**
 * Pasa las teselas de un MBTiles a archivos `{z}/{x}/{y}.<formato>` dentro de
 * una carpeta. Existe porque MapLibre 13.7 no abre `mbtiles://` (AND-R4).
 */
fun interface TileExtractor {
    fun extract(mbtiles: File, into: File)
}

/** MBTiles 1.3 usa filas TMS (origen abajo); MapLibre pide XYZ (origen arriba). */
fun xyzRow(zoom: Int, tmsRow: Int): Int = (1 shl zoom) - 1 - tmsRow

/** Extractor real con el SQLite de Android. */
object AndroidMbtilesExtractor : TileExtractor {
    override fun extract(mbtiles: File, into: File) {
        SQLiteDatabase.openDatabase(mbtiles.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val format = db.rawQuery("SELECT value FROM metadata WHERE name = 'format'", null).use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            val extension = if (format == "jpg" || format == "jpeg") "jpg" else "png"
            // ponytail: una tesela de más de ~2 MB no cabe en la ventana del cursor de Android;
            // las de un ortomosaico de 256 px pesan decenas de KB.
            db.rawQuery("SELECT zoom_level, tile_column, tile_row, tile_data FROM tiles", null).use { c ->
                while (c.moveToNext()) {
                    val z = c.getInt(0)
                    val out = File(into, "$z/${c.getInt(1)}/${xyzRow(z, c.getInt(2))}.$extension")
                    out.parentFile!!.mkdirs()
                    out.writeBytes(c.getBlob(3))
                }
            }
        }
    }
}
