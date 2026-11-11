package com.pocketube.app

import org.json.JSONArray
import org.json.JSONObject

object DownloadJson {
    fun encode(items: List<DownloadItem>): String = JSONArray().apply {
        items.forEach { item ->
            put(JSONObject().apply {
                put("id", item.id); put("runId", item.runId); put("url", item.url)
                put("title", item.title); put("author", item.author); put("thumbnail", item.thumbnail)
                put("duration", item.duration); put("quality", item.quality); put("status", item.status.name)
                put("progress", item.progress.toDouble()); put("eta", item.eta); put("filePath", item.filePath)
                put("size", item.size); put("error", item.error); put("mediaType", item.mediaType.name)
            })
        }
    }.toString()

    fun decode(value: String): List<DownloadItem> {
        val json = JSONArray(value)
        return (0 until json.length()).map { index ->
            val o = json.getJSONObject(index)
            DownloadItem(
                id = o.getString("id"), runId = o.getString("runId"), url = o.getString("url"),
                title = o.getString("title"), author = o.getString("author"), thumbnail = o.getString("thumbnail"),
                duration = o.getInt("duration"), quality = o.getInt("quality"),
                status = DownloadStatus.valueOf(o.getString("status")),
                progress = o.optDouble("progress", 0.0).toFloat(), eta = o.optLong("eta"),
                filePath = o.optString("filePath"), size = o.optLong("size"), error = o.optString("error"),
                // Version 1.0 stored video downloads without this field.
                mediaType = MediaType.valueOf(o.optString("mediaType", MediaType.VIDEO.name))
            )
        }
    }
}
