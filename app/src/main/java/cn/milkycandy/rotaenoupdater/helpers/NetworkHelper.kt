package cn.milkycandy.rotaenoupdater.helpers

import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class NetworkHelper {
    private val client = OkHttpClient()

    fun postGameData(url: String, objectId: String, gameSaveData: String): String? {
        val json = JsonObject().apply {
            addProperty("object-id", objectId)
            addProperty("save-data", gameSaveData)
        }

        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder().url(url).post(requestBody).build()

        return try {
            val response = client.newCall(request).execute()
            response.body?.string()
        } catch (e: IOException) {
            null
        }
    }
}