package me.luiscruzandresgomezrodolfohernandez.geolux.data.fake

import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.*

// Generado una vez desde contracts/geolux-v1/fixtures/finca-demo (mismos ids,
// valores y coordenadas). FakeDataTest comprueba que sigue igual a expected.json.
internal val FINCA_DEMO = ProjectData(
    packageId = "6c1f1d3e-4f0a-4b8e-9d55-2f6a0c9b7e10",
    packageCreatedAt = "2026-10-09T12:00:00+00:00",
    project = Project(
        id = "a1b2c3d4-0000-4000-8000-000000000001", name = "Finca Demo GeoLux", description = "Finca sintética para el contrato .geolux v1",
        location = "Vichada, Colombia", farmId = null,
        center = Position(-70.1985, 6.301),
        area = Measure(22.5, "ha"),
        createdAt = "2026-10-01T12:00:00+00:00", updatedAt = "2026-10-01T12:00:00+00:00",
    ),
    workArea = WorkArea("wa-demo", listOf(listOf(Position(-70.201, 6.299), Position(-70.196, 6.299), Position(-70.196, 6.303), Position(-70.201, 6.303), Position(-70.201, 6.299))), 245000.0, 2020.5),
    zones = listOf(
        Zone("zone-demo-1", "Potrero La Ceiba", "", "active", "Activo", "#2A9D8F", listOf(listOf(Position(-70.2, 6.3), Position(-70.197, 6.3), Position(-70.197, 6.302), Position(-70.2, 6.302), Position(-70.2, 6.3))), 73500.0, 1100.0, 40.0),
        Zone("zone-demo-2", "Potrero El Bajo", "", "resting", "En descanso", "#2A9D8F", listOf(listOf(Position(-70.197, 6.3), Position(-70.196, 6.3), Position(-70.196, 6.302), Position(-70.197, 6.302), Position(-70.197, 6.3))), 73500.0, 1100.0, null),
    ),
    elements = listOf(
        MapElement("el-demo-1", "tank", "Tanque", "Tanque principal", "", Position(-70.19895, 6.30105)),
        MapElement("el-demo-2", "trough", "Bebedero", "Bebedero 1", "", Position(-70.1981, 6.3015)),
        MapElement("el-demo-3", "house", "Casa", "Casa", "", Position(-70.2005, 6.2995)),
    ),
    lines = listOf(
        MapLine("line-demo-1", "fence", "Cerca", "Cerca norte", "", 442.6, listOf(Position(-70.2, 6.302), Position(-70.196, 6.302))),
        MapLine("line-demo-2", "pipeline", "Tubería", "Tubería tanque-bebedero", "", 106.4, listOf(Position(-70.19895, 6.30105), Position(-70.1981, 6.3015))),
    ),
    missions = listOf(
        Mission("a1b2c3d4-0000-4000-8000-0000000000f1", "Vuelo Potrero La Ceiba", "valid", "Válida", 60.0, 1.64, 5.0, 75.0, 65.0, 48, 4, 1310.0, 262.0, "viable", "2026-10-01T12:00:00+00:00", listOf(listOf(Position(-70.2, 6.3), Position(-70.197, 6.3), Position(-70.197, 6.302), Position(-70.2, 6.302), Position(-70.2, 6.3)))),
    ),
    missionPaths = listOf(
        MissionPath("a1b2c3d4-0000-4000-8000-0000000000f1", "a1b2c3d4-0000-4000-8000-0000000000f1", 60.0, 4, listOf(Position(-70.1999, 6.3001), Position(-70.1971, 6.3001), Position(-70.1971, 6.3004), Position(-70.1999, 6.3004))),
    ),
    products = listOf(
        Product("model-demo", "model3d", "a1b2c3d4-0000-4000-8000-0000000000f1", "Modelo 3D sintético", "2026-10-01T12:00:00+00:00", "synthetic", "EPSG:4326", listOf(-70.2, 6.3, -70.197, 6.302), listOf("products/model-demo/model.glb"), OriginPoint(6.3, -70.2, 0.0), "m"),
        Product("ortho-demo", "orthomosaic", "a1b2c3d4-0000-4000-8000-0000000000f1", "Ortomosaico sintético", "2026-10-01T12:00:00+00:00", "synthetic", "EPSG:3857", listOf(-70.2026, 6.297, -70.1971, 6.3025), listOf("products/ortho-demo/ortho.mbtiles"), null, null),
    ),
)
