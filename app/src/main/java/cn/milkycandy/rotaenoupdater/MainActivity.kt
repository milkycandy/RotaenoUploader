package cn.milkycandy.rotaenoupdater

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.util.Patterns
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.color.DynamicColors
import com.google.android.material.snackbar.Snackbar
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Suppress("DEPRECATION")
@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {

    private lateinit var textViewLog: TextView
    private lateinit var textViewLastUploadTime: TextView
    private lateinit var textViewObjectId: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var toggleGroup: MaterialButtonToggleGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val mainLayout = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        textViewLog = findViewById(R.id.textViewLogContent)
        textViewObjectId = findViewById(R.id.textViewObjectId)
        progressBar = findViewById(R.id.progressBar)
        toggleGroup = findViewById(R.id.toggleButton)
        textViewLastUploadTime = findViewById(R.id.lastUploadTime)

        // 恢复用户的选择状态
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val selectedButtonId = sharedPreferences.getInt(PREF_KEY_SELECTED_BUTTON, View.NO_ID)
        if (selectedButtonId != View.NO_ID) {
            toggleGroup.check(selectedButtonId)
        }
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                sharedPreferences.edit {
                    putInt(PREF_KEY_SELECTED_BUTTON, checkedId)
                }
            }
        }

        showLastUploadTime()

        val settingsPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        // 检查是否已经写入过这个设置
        val isDataAccessBypassWritten =
            settingsPreferences.getBoolean("data_access_bypass_written", false)

        // 如果尚未写入过即为首次启动，如果系统版本是安卓11及以上，则写入true（开启data限制绕过），并设置写入标记
        if (!isDataAccessBypassWritten && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            settingsPreferences.edit().putBoolean("data_access_bypass", true)
                .putBoolean("data_access_bypass_written", true).apply()
        }

        requestPermissions()

        textViewObjectId.setOnClickListener { copyToClipboard(textViewObjectId.text) }

        // 初始化上传MaterialCardView
        val cardUpload = findViewById<View>(R.id.card_upload)
        cardUpload.setOnClickListener {
            // 如果进度条可见，即为正在上传，不响应点击
            if (progressBar.visibility == View.VISIBLE) return@setOnClickListener
            val checkedButtonId = toggleGroup.checkedButtonId
            if (checkedButtonId == View.NO_ID) {
                showSnackBar("请先选择一个版本")
            } else {
                // 读取是否开启了data限制绕过(data_access_bypass的值)，如果开启则插入零宽空格以绕过限制
                // 这个绕过方法并不适用于所有设备 仅在小米14 Pro HyperOS 1.0.42.0.UNBCNXM 安全更新2024-06-01 测试可用，其他设备没试，主要是没有
                val dataAccessBypass = settingsPreferences.getBoolean("data_access_bypass", false)
                val gamePath = if (dataAccessBypass) {
                    when (checkedButtonId) {
                        R.id.buttonPlay -> "Andro\u200Bid/data/com.xd.rotaeno.googleplay"
                        R.id.buttonGlobal -> "Andro\u200Bid/data/com.xd.rotaeno.tapio"
                        R.id.buttonChina -> "Andro\u200Bid/data/com.xd.rotaeno.tapcn"
                        else -> null
                    }
                } else {
                    when (checkedButtonId) {
                        R.id.buttonPlay -> "Android/data/com.xd.rotaeno.googleplay"
                        R.id.buttonGlobal -> "Android/data/com.xd.rotaeno.tapio"
                        R.id.buttonChina -> "Android/data/com.xd.rotaeno.tapcn"
                        else -> null
                    }
                }
                if (gamePath != null) {
                    getGameData(gamePath)
                    // 保存上一次的上传时间
                    sharedPreferences.edit {
                        putLong(PREF_KEY_LAST_UPLOAD_TIME, System.currentTimeMillis())
                    }
                }
            }
        }
        checkDeveloperBirthday()
        showDeviceInfo()
    }

    private fun showDeviceInfo() {
        val deviceManufacturer = Build.MANUFACTURER
        val deviceModel = Build.MODEL
        val androidVersion = Build.VERSION.RELEASE
        val securityPatch = Build.VERSION.SECURITY_PATCH
        appendLog("设备：$deviceManufacturer | $deviceModel\n系统: Android $androidVersion | 安全补丁 $securityPatch")
    }

    private fun showLastUploadTime() {
        // 显示上次上传时间
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastUploadTime = sharedPreferences.getLong(PREF_KEY_LAST_UPLOAD_TIME, 0L)
        if (lastUploadTime != 0L) {
            val lastUploadDate = Date(lastUploadTime)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            runOnUiThread {
                textViewLastUploadTime.text = "上次上传于: ${dateFormat.format(lastUploadDate)}"
            }
        } else {
            runOnUiThread {
                textViewLastUploadTime.text = "从未上传过"
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings -> {
                // 启动 SettingsActivity
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "请为RotaenoUploader授予文件访问权限！", Toast.LENGTH_LONG)
                    .show()
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivityForResult(intent, MANAGE_EXTERNAL_STORAGE_REQUEST_CODE)
            }
        } else {
            // Android 11以下的权限请求
            val permissions = arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            requestPermissions(permissions, STORAGE_PERMISSION_REQUEST_CODE)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MANAGE_EXTERNAL_STORAGE_REQUEST_CODE) {
            // 上面判断过一次了，但是这里不判断又会warning，那再判断一次好了，也挺保险的
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
        appendLog("正在尝试获取游戏数据...")
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
                    Log.d("MainActivity", "GameSave file path: $gameSaveFilePath")
                    val gameSaveFile = File(gameSaveFilePath)
                    if (gameSaveFile.exists() && gameSaveFile.isFile) {
                        try {
                            contentResolver.openInputStream(Uri.fromFile(gameSaveFile))
                                .use { inputStream ->
                                    val fileContentBytes = inputStream?.readBytes()
                                    fileContentBytes?.let { bytes ->
                                        val encodedContent =
                                            Base64.encodeToString(bytes, Base64.DEFAULT)
                                        appendLog("正在发送数据到服务器...")
                                        postGameData(objectId, encodedContent)
                                    } ?: run {
                                        appendLog("GameSave文件为空或无法读取")
                                        hideLoading()
                                    }
                                }
                        } catch (e: IOException) {
                            appendLog("读取GameSave文件时出错：${e.message}")
                            Log.e("RotaenoUploader", "Error reading GameSave file", e)
                            hideLoading()
                        }
                    } else {
                        appendLog("错误：GameSaveFile doesn't exist or isn't file")
                        hideLoading()
                    }
                } catch (e: Exception) {
                    appendLog("读取文件失败: ${e.message}")
                    hideLoading()
                }
            } else {
                appendLog("失败，文件不存在或无法访问")
                hideLoading()
            }
        }
    }


    private fun postGameData(objectId: String, gameSaveData: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val delayedCheck = launch {
                delay(6000)
                appendLog("目标服务器响应缓慢，仍在上传中...")
            }

            val json = JsonObject()
            json.addProperty("object-id", objectId)
            json.addProperty("save-data", gameSaveData)

            val jsonString = json.toString()

//          appendLog("即将发送的数据: $jsonString")

            val requestBody =
                jsonString.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            val sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(applicationContext)

            var url = sharedPreferences.getString("remote_server_address", "")

            // 留空就是默认
            if (url == "" || url == null) {
                showSnackBar("服务器地址未设置，请前往设置")
                hideLoading()
                delayedCheck.cancel()
                return@launch
            }
            if (!Patterns.WEB_URL.matcher(url).matches()) {
                showSnackBar("服务器地址无效")
                appendLog("无效的服务器地址: $url")
                hideLoading()
                delayedCheck.cancel()
                return@launch
            }

            val request = Request.Builder().url(url.toString()).post(requestBody).build()

            val client = OkHttpClient()
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                appendLog("来自Bot的回复: $responseBody")
                showLastUploadTime()
            } catch (e: IOException) {
                appendLog("发送数据失败: ${e.message}")
            } finally {
                delayedCheck.cancel()
                hideLoading()
            }
        }
    }

    private fun copyToClipboard(text: CharSequence) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("objectId", text)
        clipboard.setPrimaryClip(clip)
        showSnackBar("已复制ObjectId到剪切板")
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

    private fun showSnackBar(message: String) {
        runOnUiThread {
            Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show()
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

    private fun checkDeveloperBirthday() {
        val calendar = Calendar.getInstance()
        val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
        val month = calendar.get(Calendar.MONTH) + 1 // 月份是从0开始的，所以需要+1

        // 检查当前日期是否是6月25日
        if (month == 6 && dayOfMonth == 25) {
            // 如果是6月25日，显示SnackBar
            val view = findViewById<View>(android.R.id.content)
            Snackbar.make(
                view, "你知道吗？\n今天是这个软件的开发者 大块牛奶糖 的生日", Snackbar.LENGTH_LONG
            ).show()
        }
    }

    companion object {
        private const val PREF_KEY_LAST_UPLOAD_TIME = "last_upload_time"
        private const val PREF_KEY_SELECTED_BUTTON = "selected_button"
        private const val PREFS_NAME = "RotaenoUploaderPrefs"
        private const val MANAGE_EXTERNAL_STORAGE_REQUEST_CODE = 1002
        private const val STORAGE_PERMISSION_REQUEST_CODE = 114
    }
}