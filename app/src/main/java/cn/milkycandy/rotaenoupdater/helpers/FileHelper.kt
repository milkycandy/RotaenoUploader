package cn.milkycandy.rotaenoupdater.helpers

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class FileHelper(private val context: Context) {
    fun readUserData(path: String): JsonObject? {
        val file = File(path)
        return if (file.exists() && file.isFile) {
            try {
                JsonParser.parseString(file.readText()).asJsonObject
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    fun readGameSaveFile(path: String): ByteArray? {
        val file = File(path)
        return if (file.exists() && file.isFile) {
            try {
                context.contentResolver.openInputStream(Uri.fromFile(file))?.use { it.readBytes() }
            } catch (e: IOException) {
                null
            }
        } else {
            null
        }
    }

    fun handleDocumentResult(documentUri: Uri): Pair<String?, ByteArray?> {
        val documentFile = DocumentFile.fromTreeUri(context, documentUri) ?: return Pair(null, null)
        val filesFolder = documentFile.findFile("files") ?: return Pair(null, null)
        val rotaenoFolder = filesFolder.findFile("RotaenoLC") ?: return Pair(null, null)
        val userDataFile = rotaenoFolder.findFile(".userdata") ?: return Pair(null, null)

        if (!userDataFile.exists() || !userDataFile.isFile) {
            return Pair(null, null)
        }

        val jsonString = context.contentResolver.openInputStream(userDataFile.uri)?.bufferedReader()?.use { it.readText() }
        val jsonObject = JsonParser.parseString(jsonString).asJsonObject
        val objectId = jsonObject.get("objectId").asString

        val gameSaveFileName = sha256ToHex("GameSave$objectId")
        val gameSaveFile = filesFolder.findFile(gameSaveFileName)

        if (gameSaveFile == null || !gameSaveFile.exists() || !gameSaveFile.isFile) {
            return Pair(objectId, null)
        }

        val gameSaveData = context.contentResolver.openInputStream(gameSaveFile.uri)?.use { it.readBytes() }
        return Pair(objectId, gameSaveData)
    }

    fun sha256ToHex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}