package com.example.util

import org.json.JSONArray

object SubTaskUtils {
    /**
     * Extracts individual sub-task strings from a task's bulletPoints text.
     */
    fun parseSubTasks(bulletPoints: String?): List<String> {
        if (bulletPoints.isNullOrBlank()) return emptyList()
        return bulletPoints.lines()
            .map { line ->
                line.trim()
                    .removePrefix("•")
                    .removePrefix("-")
                    .removePrefix("*")
                    .trim()
            }
            .filter { it.isNotBlank() }
    }

    /**
     * Serializes a list of sub-task strings into a JSON string.
     */
    fun listToJson(items: List<String>): String {
        val array = JSONArray()
        items.forEach { array.put(it) }
        return array.toString()
    }

    /**
     * Deserializes a JSON string back into a list of sub-task strings.
     */
    fun jsonToList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
            list
        } catch (e: Exception) {
            json.lines().map { it.trim().removePrefix("•").removePrefix("-").trim() }.filter { it.isNotBlank() }
        }
    }
}
