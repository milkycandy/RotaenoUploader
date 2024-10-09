package cn.milkycandy.rotaenoupdater.helpers

import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class NetworkHelper {
    data class PostResult(
        val isSuccess: Boolean,
        val errorMessage: String? = null
    )
    private val client = OkHttpClient()

    fun postGameData(url: String, objectId: String, gameSaveData: String): PostResult {
        val json = JsonObject().apply {
            addProperty("object-id", objectId)
            addProperty("save-data", gameSaveData)
        }

        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder().url(url).post(requestBody).build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                PostResult(true) // 成功，没有错误信息
            } else {
                PostResult(false, "Error: ${response.code} ${response.message}") // 请求失败，返回错误信息
            }
        } catch (e: IOException) {
            PostResult(false, "Exception: ${e.message}") // 捕获异常并返回错误信息
        }
    }
}
