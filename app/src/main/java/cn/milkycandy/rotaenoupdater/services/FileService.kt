package cn.milkycandy.rotaenoupdater.services

import android.net.Uri
import android.util.Base64
import android.util.Log
import cn.milkycandy.rotaenoupdater.models.BeanFile
import java.io.File
import java.io.IOException

class FileService : IFileService.Stub() {
    override fun listFiles(path: String): List<BeanFile> {
        val list = mutableListOf<BeanFile>()
        val files = File(path).listFiles()
        files?.forEach { file ->
            list.add(
                BeanFile(
                    name = file.name,
                    path = file.path,
                    isDir = file.isDirectory,
                    isGrantedPath = false,
                    pathPackageName = file.name
                )
            )
        }
        return list
    }

    override fun readFile(path: String): String {
        try {
            val file = File(path)
            return if (file.exists() && file.isFile) {
                val content = file.readText()
                content
            } else {
                "Error: File does not exist or is not a file"
            }
        } catch (e: Exception) {
            return "Error reading file: ${e.message}"
        }
    }

    override fun readBytesAndEncode(path: String): String {
        try {
            val file = File(path)
            file.readBytes().let { bytes ->
                return Base64.encodeToString(bytes, Base64.DEFAULT)
            }
        } catch (e: IOException) {
            return "Error reading file: ${e.message}"
        }
    }

}
