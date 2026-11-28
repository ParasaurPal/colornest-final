package com.yourname.colornest.data

import androidx.room.TypeConverter
import org.json.JSONArray

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return JSONArray().apply {
            value?.forEach { put(it) }
        }.toString()
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()

        // New values use JSON so tags containing commas round-trip correctly.
        if (value.trimStart().startsWith("[")) {
            return runCatching {
                val array = JSONArray(value)
                buildList {
                    for (index in 0 until array.length()) {
                        array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }.getOrElse { emptyList() }
        }

        // Backward compatibility for v1-v4 databases that stored comma-separated lists.
        return value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}
