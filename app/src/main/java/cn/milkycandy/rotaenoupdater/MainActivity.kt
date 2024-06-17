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
import android.view.RoundedCorner
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
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

        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val settingsPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        // 检查是否已经写入过这个设置
        val isFirstRun =
            sharedPreferences.getBoolean("is_first_run", true)

        if (isFirstRun) {
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.putExtra("source", "MainActivity")
            startActivity(intent)
            finish()
            return
        }

        DynamicColors.applyToActivityIfAvailable(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.toolbar)) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply the insets as a margin to the view. This solution sets
            // only the top dimension, but you can apply whichever
            // insets are appropriate to your layout. You can also update the view padding
            // if that's more appropriate.
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top
            }

            // Return CONSUMED if you don't want the window insets to keep passing
            // down to descendant views.
            WindowInsetsCompat.CONSUMED
        }

        setCornerRadius()

        textViewLog = findViewById(R.id.textViewLogContent)
        textViewObjectId = findViewById(R.id.textViewObjectId)
        progressBar = findViewById(R.id.progressBar)
        toggleGroup = findViewById(R.id.toggleButton)
        textViewLastUploadTime = findViewById(R.id.lastUploadTime)

        // 恢复用户的选择状态
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

        val selectedMode = settingsPreferences.getString("selected_mode", null)
        if (selectedMode == "traditional") {
            requestPermissions()
        }
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
                val gamePath = when (checkedButtonId) {
                    R.id.buttonPlay -> "Android/data/com.xd.rotaeno.googleplay"
                    R.id.buttonGlobal -> "Android/data/com.xd.rotaeno.tapio"
                    R.id.buttonChina -> "Android/data/com.xd.rotaeno.tapcn"
                    else -> null
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

    private fun setCornerRadius() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cardLog: MaterialCardView = findViewById(R.id.card_log)

            // 获取系统窗口插图
            val rootView = window.decorView.rootView
            rootView.setOnApplyWindowInsetsListener { _, insets ->
                val bottomRight = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)
                    ?: return@setOnApplyWindowInsetsListener insets
                cardLog.radius = bottomRight.radius.toFloat()
                insets
            }
        }
    }

    private fun showDeviceInfo() {
        val deviceManufacturer = Build.MANUFACTURER
        val deviceBrand = Build.BRAND
        val deviceModel = Build.MODEL
        val androidVersion = Build.VERSION.RELEASE
        val securityPatch = Build.VERSION.SECURITY_PATCH

        // 判断品牌和制造商是否一致
        val brandManufacturerDisplay = if (deviceManufacturer == deviceBrand) {
            deviceBrand // 只显示品牌
        } else {
            "$deviceBrand ($deviceManufacturer)" // 显示品牌和制造商
        }
        val versionName = getString(R.string.app_version)
        appendLog("设备：$brandManufacturerDisplay | $deviceModel\n系统：Android $androidVersion | 安全补丁 $securityPatch\n上传器版本：$versionName")
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

        if (data == null || resultCode != RESULT_OK) {
            if (requestCode == DATA_REQUEST_CODE) {
                Toast.makeText(this, "操作被取消", Toast.LENGTH_SHORT).show()
                hideLoading()
            }
            return
        }
        if (requestCode == DATA_REQUEST_CODE) {
            val documentUri = data.data ?: return
            val documentFile = DocumentFile.fromTreeUri(this, documentUri) ?: return
            val filesFolder = documentFile.findFile("files") ?: return
            val rotaenoFolder = filesFolder.findFile("RotaenoLC") ?: return
            val userDataFile = rotaenoFolder.findFile(".userdata") ?: return

            if (!userDataFile.exists() || !userDataFile.isFile) {
                appendLog("失败，.userdata文件不存在或无法访问")
                hideLoading()
                return
            }

            try {
                val jsonString = contentResolver.openInputStream(userDataFile.uri)?.bufferedReader()
                    ?.use { it.readText() }
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                val objectId = jsonObject.get("objectId").asString
                Log.d("RotaenoUploader", "objectId: $objectId")
                runOnUiThread {
                    textViewObjectId.text = objectId
                }
                val gameSaveFileName = sha256ToHex("GameSave$objectId")
                val gameSaveFile = filesFolder.findFile(gameSaveFileName) ?: return

                if (!gameSaveFile.exists() || !gameSaveFile.isFile) {
                    appendLog("错误：GameSave不存在或不是文件")
                    hideLoading()
                    return
                }
                var fileContentBytes: ByteArray?
                gameSaveFile?.let {
                    try {
                        contentResolver.openInputStream(it.uri).use { inputStream ->
                            fileContentBytes = inputStream?.readBytes()
                            fileContentBytes?.let { bytes ->
                                val encodedContent = Base64.encodeToString(bytes, Base64.DEFAULT)
                                // appendLog("Encoded GameSave file:\n$encodedContent")

                                appendLog("正在发送数据到服务器...")
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
                }

            } catch (e: Exception) {
                appendLog("读取文件失败: ${e.message}")
                hideLoading()
            }
        }

    }

    private fun getGameData(path: String) {
        val settingsPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val selectedMode = settingsPreferences.getString("selected_mode", null)
        var processedPath = path
        val dataAccessBypass = settingsPreferences.getBoolean("data_access_bypass", false)
        if (dataAccessBypass) {
            Log.d("RotaenoUploader", "已开启data绕过")
            processedPath = processedPath.replace("Android", "Andro\u200Bid")
        }
        when (selectedMode) {
            "traditional" -> {
                getGameDataByFile(processedPath)
                Log.d("RotaenoUploader", "File Path: $processedPath")
            }

            "saf" -> {
                processedPath = processedPath.replace("/", "%2F")
                getGameDataBySAF(processedPath)
                Log.d("RotaenoUploader", "SAF Path: $processedPath")
            }

            else -> {
                Toast.makeText(this, "未知的模式！", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun getGameDataBySAF(path: String) {
        showLoading()
        val uri =
            Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A$path/document/primary%3A$path")
        val intent = Intent("android.intent.action.OPEN_DOCUMENT_TREE")
        intent.putExtra("android.provider.extra.INITIAL_URI", uri)
        Toast.makeText(this, "请直接点击底部的“使用此文件夹”", Toast.LENGTH_SHORT).show()
        startActivityForResult(intent, DATA_REQUEST_CODE)
    }

    private fun getGameDataByFile(path: String) {
        showLoading()
        Log.d("RotaenoUploader", "Path: $path")
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
            if (url.isNullOrEmpty()) {
                url = "http://rotaeno.api.mihoyo.pw/decryptAndSaveGameData"
                appendLog("正在使用默认服务器地址")
//                showSnackBar("服务器地址未设置，请前往设置")
//                hideLoading()
//                delayedCheck.cancel()
//                return@launch
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
        private const val MANAGE_EXTERNAL_STORAGE_REQUEST_CODE = 514
        private const val STORAGE_PERMISSION_REQUEST_CODE = 114
        private const val DATA_REQUEST_CODE = 1919
    }
}