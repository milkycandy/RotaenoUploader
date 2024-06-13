package cn.milkycandy.rotaenoupdater

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.provider.Settings
import android.view.Window
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.color.DynamicColors
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
import java.io.File
import java.io.IOException
import java.security.MessageDigest

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {

    private lateinit var dataButton: Button
    private lateinit var dataButtonPlay: Button
    private lateinit var dataButtonTapGlobal: Button
    private lateinit var textViewLog: TextView
    private lateinit var textViewObjectId: TextView
    private lateinit var textViewDeveloper: TextView
    private lateinit var progressBar: ProgressBar
    private var developerUrl: String? = null

    private val MANAGE_EXTERNAL_STORAGE_REQUEST_CODE = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        // Ensure the window content fits the system windows
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Apply window insets to the main layout
        val mainLayout = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Set padding to handle system bars
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        dataButtonPlay = findViewById(R.id.button_data_play)
        dataButtonTapGlobal = findViewById(R.id.button_data_tap_global)
        dataButton = findViewById(R.id.button_data)
        textViewLog = findViewById(R.id.textViewLogContent)
        textViewObjectId = findViewById(R.id.textViewObjectId)
        textViewDeveloper = findViewById(R.id.textViewDeveloper)
        progressBar = findViewById(R.id.progressBar)

        // 检查并请求权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "请为RotaenoUploader授予文件访问权限", Toast.LENGTH_SHORT).show()
                requestManageExternalStoragePermission()
            }
        } else {
            // Android 11以下的权限请求
            Toast.makeText(this, "系统版本为Android 11以下", Toast.LENGTH_SHORT).show()
        }

        dataButtonPlay.setOnClickListener { getGameData("Andro\u200Bid/data/com.xd.rotaeno.googleplay") }
        dataButtonTapGlobal.setOnClickListener { getGameData("Andro\u200Bid/data/com.xd.rotaeno.tapio") }
        dataButton.setOnClickListener { getGameData("Andro\u200Bid/data/com.xd.rotaeno.tapcn") }
        textViewObjectId.setOnClickListener { copyToClipboard(textViewObjectId.text) }
        fetchAndDisplayDeveloperInfo()
//        textViewDeveloper.setOnClickListener {
//            // 检查developerUrl是否不为空
//            developerUrl?.let { url ->
//                // 判断是否为哔哩哔哩链接
//                if (url.contains("bilibili.com")) {
//                    // 构建一个Intent来尝试直接使用哔哩哔哩应用打开链接
//                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
//                        setPackage("tv.danmaku.bili") // 指定哔哩哔哩包名
//                    }
//                    try {
//                        startActivity(intent)
//                    } catch (e: ActivityNotFoundException) {
//                        // 哔哩哔哩应用不可用，回退到默认逻辑
//                        fallbackToBrowser(url)
//                    }
//                } else {
//                    // 不是哔哩哔哩链接，使用默认方式打开
//                    fallbackToBrowser(url)
//                }
//            } ?: run {
//                // 如果developerUrl为空，显示Toast消息
//                Toast.makeText(this, "未获取到链接", Toast.LENGTH_LONG).show()
//            }
//        }
    }

    private fun requestManageExternalStoragePermission() {
        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        startActivityForResult(intent, MANAGE_EXTERNAL_STORAGE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MANAGE_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Toast.makeText(this, "所有文件访问权限授予成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "未授予所有文件访问权限！", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun getGameData(path: String) {
        showLoading()
        appendLog("尝试获取游戏数据...")
        CoroutineScope(Dispatchers.IO).launch {
            val filePath = "/storage/emulated/0/$path/files/RotaenoLC/.userdata"
            Log.d("MainActivity", "File path: $filePath")
            val file = File(filePath)

            if (file.exists() && file.isFile) {
                try {
                    val jsonString = file.readText()
                    val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                    val objectId = jsonObject.get("objectId").asString
                    runOnUiThread {
                        textViewObjectId.text = objectId
                    }
                    val gameSaveFileName = sha256ToHex("GameSave$objectId")
                    val gameSaveFilePath = "/storage/emulated/0/$path/files/$gameSaveFileName"
                    val gameSaveFile = File(gameSaveFilePath)
                    if (gameSaveFile.exists() && gameSaveFile.isFile) {
                        try {
                            contentResolver.openInputStream(Uri.fromFile(gameSaveFile)).use { inputStream ->
                                val fileContentBytes = inputStream?.readBytes()
                                fileContentBytes?.let { bytes ->
                                    val encodedContent = Base64.encodeToString(bytes, Base64.DEFAULT)
                                    appendLog("POST request...")
                                    postGameData(objectId, encodedContent)
                                } ?: run {
                                    appendLog("GameSave文件为空或无法读取")
                                    hideLoading()
                                }
                            }
                        } catch (e: IOException) {
                            appendLog("读取GameSave文件时出错：${e.message}")
                            Log.e("RotaenoUpdater", "Error reading GameSave file", e)
                            hideLoading()
                        }
                    } else {
                        appendLog("GameSave文件不存在")
                        hideLoading()
                    }
                } catch (e: Exception) {
                    appendLog("读取文件失败: ${e.message}")
                    hideLoading()
                }
            } else {
                appendLog("文件不存在")
                hideLoading()
            }
        }
    }


    private fun postGameData(objectId: String, gameSaveData: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val delayedCheck = launch {
                delay(6000)  // 等待5秒
                appendLog("目标服务器响应缓慢，仍在上传中...")
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


                appendLog("来自Bot的回复: $responseBody")
                hideLoading()

            } catch (e: IOException) {

                appendLog("发送数据失败: ${e.message}")
                hideLoading()

            } finally {
                delayedCheck.cancel()
            }
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
        runOnUiThread {
            if (addNewLine) {
                textViewLog.append(message + "\n")
            } else {
                textViewLog.append(message)
            }
        }
    }

    private fun showLoading() {
        runOnUiThread {
            progressBar.visibility = View.VISIBLE
        }
    }

    private fun hideLoading() {
        runOnUiThread {
            progressBar.visibility = View.GONE
        }
    }

    private fun fetchAndDisplayDeveloperInfo() {
        Log.d("RotaenoUploader", "Requesting developer information...")
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
                        val developerInfo = jsonObject.get("AboutDeveloper7").asString
                        developerUrl = jsonObject.get("url").asString
                        runOnUiThread {
                            textViewDeveloper.text = developerInfo
                        }
                    }
                } else {
                    Log.e("RotaenoUploader", "Request for developer information failed.")
                }
            } catch (e: Exception) {
                Log.e("RotaenoUploader", "Error fetching developer info.", e)
            }
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
}