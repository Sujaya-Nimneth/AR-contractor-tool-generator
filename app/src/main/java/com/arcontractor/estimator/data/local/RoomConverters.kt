package com.arcontractor.estimator.data.local

import androidx.room.TypeConverter
import com.arcontractor.estimator.domain.geometry.Vec3
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class VertexDto(
    val x: Double,
    val y: Double,
    val z: Double
)

class RoomConverters {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @TypeConverter
    fun verticesToJson(vertices: List<Vec3>): String =
        json.encodeToString(vertices.map { vertex -> vertex.toVertexDto() })

    @TypeConverter
    fun jsonToVertices(value: String): List<Vec3> =
        json.decodeFromString<List<VertexDto>>(value).map { vertex -> vertex.toDomain() }

    @TypeConverter
    fun normalToJson(normal: Vec3?): String? =
        normal?.let { json.encodeToString(it.toVertexDto()) }

    @TypeConverter
    fun jsonToNormal(value: String?): Vec3? =
        value?.let { json.decodeFromString<VertexDto>(it).toDomain() }

    private fun VertexDto.toDomain() = Vec3(x = x, y = y, z = z)

    private fun Vec3.toVertexDto() = VertexDto(
        x = x,
        y = y,
        z = z
    )
}
