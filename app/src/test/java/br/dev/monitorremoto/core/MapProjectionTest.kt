package br.dev.monitorremoto.core

import org.junit.Assert.*
import org.junit.Test

class MapProjectionTest {
    @Test fun coordenadasDeOrigemNoCentroDoMundo() {
        assertEquals(MapProjection.scale(5) / 2, MapProjection.x(0.0, 5), 1e-7)
        assertEquals(MapProjection.scale(5) / 2, MapProjection.y(0.0, 5), 1e-7)
    }
    @Test fun idaEVoltaEmLatitudesBrasileiras() {
        val examples = listOf(-20.176 to -45.711, -23.55 to -46.63, 51.5 to -0.12)
        examples.forEach { (lat, lon) ->
            val x = MapProjection.x(lon, 14)
            val y = MapProjection.y(lat, 14)
            assertEquals(lat, MapProjection.lat(y, 14), 1e-5)
            assertEquals(lon, MapProjection.lon(x, 14), 1e-5)
        }
    }
    @Test fun extremosEZoomSeguros() {
        assertEquals(-180.0, MapProjection.longitude(180.0), 1e-9)
        assertEquals(0, MapProjection.normalizedTileX(-8, 3))
        assertEquals(7, MapProjection.normalizedTileX(-1, 3))
        assertEquals(0, MapProjection.normalizedTileX(8, 3))
        assertTrue(MapProjection.y(-90.0, 17) <= MapProjection.scale(17))
        assertTrue(MapProjection.metersPerPixel(-20.0, 15) > 0.0)
    }
}
