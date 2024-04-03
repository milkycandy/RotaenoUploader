package cn.milkycandy.rotaenoupdater

import android.annotation.SuppressLint
import android.app.Activity
import androidx.appcompat.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {

    private lateinit var dataButton: Button
    private lateinit var dataButtonPlay: Button
    private lateinit var dataButtonTapGlobal: Button
    private lateinit var dataButtonManual: Button
    private lateinit var buttonShowAuthorizationFailedDialog: Button
    private lateinit var textViewLog: TextView
    private lateinit var textViewObjectId: TextView
    private lateinit var textViewDeveloper: TextView
    private lateinit var progressBar: ProgressBar
    private var currentSelectingPath: String? = null
    private var developerUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        dataButtonPlay = findViewById(R.id.button_data_play)
        dataButtonTapGlobal = findViewById(R.id.button_data_tap_global)
        dataButton = findViewById(R.id.button_data)
        dataButtonManual = findViewById(R.id.button_manual)
        buttonShowAuthorizationFailedDialog = findViewById(R.id.button_open_documentsui)
        textViewLog = findViewById(R.id.textViewLogContent)
        textViewObjectId = findViewById(R.id.textViewObjectId)
        textViewDeveloper = findViewById(R.id.textViewDeveloper)
        progressBar = findViewById(R.id.progressBar)

        dataButtonPlay.setOnClickListener { selectData("Android%2Fdata%2Fcom.xd.rotaeno.googleplay%2F") }
        dataButtonTapGlobal.setOnClickListener { selectData("Android%2Fdata%2Fcom.xd.rotaeno.tapio") }
        dataButton.setOnClickListener { selectData("Android%2Fdata%2Fcom.xd.rotaeno.tapcn") }
        dataButtonManual.setOnClickListener {
            // 创建一个AlertDialog.Builder实例
            AlertDialog.Builder(this)
                .setTitle("提醒")
                .setMessage("你应该选择包含files文件夹的目录（cache文件夹可能也会在一块），不要点进里面去")
                .setPositiveButton("好") { dialog, which ->
                    selectDataManually()
                }
                .setNegativeButton("坏", null)
                .show()
        }
        buttonShowAuthorizationFailedDialog.setOnClickListener { showAuthorizationFailedDialog(this) }
        textViewObjectId.setOnClickListener { copyToClipboard(textViewObjectId.text) }
        fetchAndDisplayDeveloperInfo()
        textViewDeveloper.setOnClickListener {
            // 检查developerUrl是否不为空
            developerUrl?.let { url ->
                // 判断是否为哔哩哔哩链接
                if (url.contains("bilibili.com")) {
                    // 构建一个Intent来尝试直接使用哔哩哔哩应用打开链接
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        setPackage("tv.danmaku.bili") // 指定哔哩哔哩包名
                    }
                    try {
                        startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        // 哔哩哔哩应用不可用，回退到默认逻辑
                        fallbackToBrowser(url)
                    }
                } else {
                    // 不是哔哩哔哩链接，使用默认方式打开
                    fallbackToBrowser(url)
                }
            } ?: run {
                // 如果developerUrl为空，显示Toast消息
                Toast.makeText(this, "未获取到链接", Toast.LENGTH_LONG).show()
            }
        }
        if (Build.VERSION.SDK_INT < 34) {
            buttonShowAuthorizationFailedDialog.visibility = View.GONE
        }
    }

    private fun fallbackToBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // 如果没有应用可以处理这个Intent，显示Toast消息
            Toast.makeText(this, "没有可以处理这个链接的应用", Toast.LENGTH_LONG).show()
        }
    }

    private fun getUri(path: String): String {
        return "content://com.android.externalstorage.documents/tree/primary%3A$path/document/primary%3A$path"
    }

    private fun selectData(path: String) {
        currentSelectingPath = path
        appendLog("—————————————————")

        if (!hasAccessPermission(path)) {
            val uri = Uri.parse(getUri(path))
            Log.d("MainActivity", "uri: $uri")
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                putExtra("android.provider.extra.INITIAL_URI", uri)
            }
            appendLog("正在取得data访问授权...", false)

            if (Build.VERSION.SDK_INT < 34) {
                Toast.makeText(this, "请手动点击屏幕底部的“使用此文件夹”", Toast.LENGTH_LONG).show()
            }

            startActivityForResult(intent, DATA_REQUEST_CODE)
        } else {
            // 已有授权，直接处理文件
            appendLog("您的游戏数据将被上传至外部Bot服务器处理")
            handleFile(path)
        }
    }

    private fun selectDataManually() {
        appendLog("—————————————————")
        appendLog("手动选择目录")
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra("android.provider.extra.INITIAL_URI", Uri.parse("content://com.android.externalstorage.documents/tree/primary"))
        }
        appendLog("取得目录访问授权...", false)
        Toast.makeText(this, "请在看到files文件夹后点击“使用此文件夹”，不要点进去", Toast.LENGTH_LONG).show()
        startActivityForResult(intent, MANUALLY_SELECT_DATA_REQUEST_CODE)
    }

    @SuppressLint("WrongConstant")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == DATA_REQUEST_CODE) {
                data?.data?.also { uri ->
                    val takeFlags: Int =
                        data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    contentResolver.takePersistableUriPermission(uri, takeFlags)

                    currentSelectingPath?.let { path ->
                        markAccessPermissionGranted(uri, path)
                        appendLog("成功")
                        appendLog("请再次点击上传按钮")
                    }
                    currentSelectingPath = null // 清除当前路径，防止重复使用
                }
            } else if (requestCode == MANUALLY_SELECT_DATA_REQUEST_CODE) {
                data?.data?.also { uri ->
                    appendLog("成功")
                    handleFile(uri.toString(), true)
                }
            }
        } else {
            appendLog("未授权")
            Log.d("MainActivity", "User cancel: $currentSelectingPath")
            buttonShowAuthorizationFailedDialog.visibility = View.VISIBLE
            currentSelectingPath = null // 清除当前路径
        }
    }

    fun showAuthorizationFailedDialog(context: Context) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("授权失败？")
            .setMessage("如果您看到了提示：“无法使用此文件夹。为保护您的隐私，请选择其他文件夹”\n您可能需要使用安卓存储访问框架手动将Rotaeno的数据目录复制出来，再手动选择目录上传。每次更新数据都需要重新复制。")
            .setPositiveButton("好") { dialog, which ->
                // 尝试启动指定的Activity
                val intent = Intent().apply {
                    component = android.content.ComponentName("com.google.android.documentsui", "com.android.documentsui.LauncherActivity")
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("坏") { dialog, which ->
                // 关闭对话框
                dialog.dismiss()
            }
            .create()

        dialog.show()
    }

    private fun handleFile(path: String, isPathManuallySelected: Boolean = false) {

        val uri = if (isPathManuallySelected) Uri.parse(path) else Uri.parse(getUri(path))
        try {
            val documentTree = DocumentFile.fromTreeUri(this, uri)
            val filesDir = documentTree?.findFile("files")
            val rotaenoLCDir = filesDir?.findFile("RotaenoLC")
            val userdataFile = rotaenoLCDir?.findFile(".userdata")

            userdataFile?.let {
                contentResolver.openInputStream(it.uri).use { inputStream ->
                    val jsonStr = inputStream?.bufferedReader()?.readText()
                    val jsonObject = JsonParser.parseString(jsonStr).asJsonObject
                    val objectId = jsonObject.get("objectId").asString
                    val gameSaveFileName = sha256ToHex("GameSave$objectId")

                    appendLog("objectId: $objectId")
                    handleGameSaveFile(filesDir, gameSaveFileName, objectId)
                }
            } ?: run {
                appendLog("访问.userdata文件时出错")
            }
        } catch (e: IOException) {
            appendLog("读取.userdata文件时出错")
            Log.e("RotaenoUpdater", "读取.userdata文件时出错：${e.message}", e)
        } catch (e: Exception) {
            appendLog("解析 .userdata文件时出错")
            Log.e("RotaenoUpdater", "解析 .userdata文件时出错：${e.message}", e)
        }
    }

    private fun handleGameSaveFile(filesDir: DocumentFile?, fileName: String, objectId: String) {
        var fileContentBytes: ByteArray?
        val gameSaveFile = filesDir?.findFile(fileName)

        gameSaveFile?.let {
            try {
                contentResolver.openInputStream(it.uri).use { inputStream ->
                    fileContentBytes = inputStream?.readBytes()
                    fileContentBytes?.let { bytes ->
                        val encodedContent = Base64.encodeToString(bytes, Base64.DEFAULT)
                        // appendLog("Encoded GameSave file:\n$encodedContent")

                        appendLog("POST request...")
                        postGameData(objectId, encodedContent)
                    } ?: run {
                        appendLog("GameSave文件为空或无法读取")
                    }
                }
            } catch (e: IOException) {
                appendLog("读取GameSave文件时出错：${e.message}")
                Log.e("RotaenoUpdater", "Error reading GameSave file", e)
            }
        } ?: run {
            appendLog("找不到GameSave文件")
        }
        runOnUiThread {
            textViewObjectId.text = objectId
        }
    }

    private fun postGameData(objectId: String, gameSaveData: String) {
        showLoading()
        CoroutineScope(Dispatchers.IO).launch {
            val delayedCheck = launch {
                delay(6000)  // 等待5秒
                runOnUiThread {
                    appendLog("目标服务器响应缓慢，仍在上传中...")
                }
            }

            val json = JsonObject()
            json.addProperty("object-id", objectId)
            json.addProperty("save-data", gameSaveData)

            val jsonString = json.toString()

//            runOnUiThread {
//                appendLog("即将发送的数据: $jsonString")
//            }

            val requestBody =
                jsonString.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request =
                Request.Builder().url("http://rotaeno.api.mihoyo.pw/decryptAndSaveGameData")
                    .post(requestBody).build()

            val client = OkHttpClient()
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                runOnUiThread {
                    appendLog("来自Bot的回复: $responseBody")
                    hideLoading()
                }
            } catch (e: IOException) {
                runOnUiThread {
                    appendLog("发送数据失败: ${e.message}")
                    hideLoading()
                }
            } finally {
                delayedCheck.cancel()
            }
        }
    }

    private fun markAccessPermissionGranted(uri: Uri, path: String) {
        val sharedPrefs = getSharedPreferences("Preferences", Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putBoolean("DataAccessGranted_$path", true)
            putString("PersistedUri_$path", uri.toString())
            apply()
        }
    }

    private fun hasAccessPermission(path: String): Boolean {
        val sharedPrefs = getSharedPreferences("Preferences", Context.MODE_PRIVATE)
        val persistedUriString = sharedPrefs.getString("PersistedUri_$path", null) ?: return false
        val persistedUri = Uri.parse(persistedUriString)
        return contentResolver.persistedUriPermissions.any {
            it.uri == persistedUri && it.isReadPermission
        }
    }

    private fun copyToClipboard(text: CharSequence) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("objectId", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "已复制ObjectId到剪切板", Toast.LENGTH_SHORT).show()
    }

    private fun sha256ToHex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun appendLog(message: String, addNewLine: Boolean = true) {
        if (addNewLine) {
            textViewLog.append(message + "\n")
        } else {
            textViewLog.append(message)
        }
    }

    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        progressBar.visibility = View.GONE
    }

    private fun fetchAndDisplayDeveloperInfo() {
        Log.d("RotaenoUpdater", "Requesting developer information...")
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://gitee.com/milkycandy/app-cloud-control/raw/master/rotaeno_updater.json")
                .build()

            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonData = response.body?.string()
                    jsonData?.let {
                        val jsonObject = JsonParser.parseString(it).asJsonObject
                        val developerInfo = jsonObject.get("AboutDeveloper6").asString
                        developerUrl = jsonObject.get("url").asString
                        runOnUiThread {
                            textViewDeveloper.text = developerInfo
                        }
                    }
                } else {
                    Log.e("RotaenoUpdater", "Request for developer information failed.")
                }
            } catch (e: Exception) {
                Log.e("RotaenoUpdater", "Error fetching developer info.", e)
            }
        }
    }


    companion object {
        private const val DATA_REQUEST_CODE = 114
        private const val MANUALLY_SELECT_DATA_REQUEST_CODE = 514
    }
}