package cn.milkycandy.rotaenoupdater

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
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
import androidx.preference.PreferenceManager
import cn.milkycandy.rotaenoupdater.helpers.FileHelper
import cn.milkycandy.rotaenoupdater.helpers.NetworkHelper
import cn.milkycandy.rotaenoupdater.helpers.UIHelper
import cn.milkycandy.rotaenoupdater.services.FileService
import cn.milkycandy.rotaenoupdater.services.IFileService
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import rikka.shizuku.shared.BuildConfig
import java.text.SimpleDateFormat
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

    private lateinit var fileHelper: FileHelper
    private lateinit var networkHelper: NetworkHelper
    private lateinit var uiHelper: UIHelper

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var settingsPreferences: SharedPreferences

    private val fileExplorerService: IFileService?
        get() = if (Shizuku.pingBinder()) iFileService else null
    private lateinit var USER_SERVICE_ARGS: Shizuku.UserServiceArgs
    private var iFileService: IFileService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity onCreate")

        fileHelper = FileHelper(this)
        networkHelper = NetworkHelper()
        uiHelper = UIHelper(this)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        settingsPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        checkIfFirstRun()

        setupUI()
        setCornerRadius()

        initializeViews()
        restoreSelectedState()
        showLastUploadTime()

        showDeviceInfo()
        val mode = settingsPreferences.getString("selected_mode", null)
        when (mode) {
            "traditional" -> {
                requestFilePermissions()
            }
            "shizuku" -> {
                if (checkShizukuPermission()) {
                    initializeService()
                }
            }
        }

        textViewObjectId.setOnClickListener { copyToClipboard(textViewObjectId.text) }

        setupUploadCard()
    }

    override fun onResume() {
        super.onResume()
        val mode = settingsPreferences.getString("selected_mode", null)
        Log.d(TAG, "当前模式: $mode")
        when (mode) {
            "traditional" -> {
                requestFilePermissions()
            }
            "saf" -> {
            }
            "shizuku" -> {
                setupShizukuListeners()
            }
            else -> {
                Log.e(TAG, "无效的模式: $mode")
            }
        }
    }

    private fun checkIfFirstRun(): Boolean {
        val isFirstRun = sharedPreferences.getBoolean("is_first_run", true)
        if (isFirstRun) {
            startActivity(Intent(this, WelcomeActivity::class.java).apply {
                putExtra("source", "MainActivity")
            })
            finish()
            return true
        }
        return false
    }

    private fun setupUI() {
        DynamicColors.applyToActivityIfAvailable(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.toolbar)) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = insets.top }
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setCornerRadius() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cardLog: MaterialCardView = findViewById(R.id.card_log)

            val rootView = window.decorView.rootView
            rootView.setOnApplyWindowInsetsListener { _, insets ->
                val bottomRight = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)
                    ?: return@setOnApplyWindowInsetsListener insets
                cardLog.radius = bottomRight.radius.toFloat()
                insets
            }
        }
    }

    private fun initializeViews() {
        textViewLog = findViewById(R.id.textViewLogContent)
        textViewObjectId = findViewById(R.id.textViewObjectId)
        progressBar = findViewById(R.id.progressBar)
        toggleGroup = findViewById(R.id.toggleButton)
        textViewLastUploadTime = findViewById(R.id.lastUploadTime)
    }

    private fun restoreSelectedState() {
        val selectedButtonId = sharedPreferences.getInt(PREF_KEY_SELECTED_BUTTON, View.NO_ID)
        if (selectedButtonId != View.NO_ID) {
            toggleGroup.check(selectedButtonId)
        }
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                sharedPreferences.edit { putInt(PREF_KEY_SELECTED_BUTTON, checkedId) }
            }
        }
    }

    private fun showLastUploadTime() {
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

    private fun setupUploadCard() {
        val cardUpload = findViewById<View>(R.id.card_upload)
        cardUpload.setOnClickListener {
            if (progressBar.visibility == View.VISIBLE) return@setOnClickListener
            val checkedButtonId = toggleGroup.checkedButtonId
            if (checkedButtonId == View.NO_ID) {
                uiHelper.showSnackBar("请先选择一个版本")
            } else {
                val packageName = when (checkedButtonId) {
                    R.id.buttonPlay -> "com.xd.rotaeno.googleplay"
                    R.id.buttonGlobal -> "com.xd.rotaeno.tapio"
                    R.id.buttonChina -> "com.xd.rotaeno.tapcn"
                    else -> null
                }
                if (packageName != null) {
                    getGameData(packageName)
                }
            }
        }
    }

    private fun showDeviceInfo() {
        val deviceManufacturer = Build.MANUFACTURER
        val deviceBrand = Build.BRAND
        val deviceModel = Build.MODEL
        val androidVersion = Build.VERSION.RELEASE
        val securityPatch = Build.VERSION.SECURITY_PATCH

        val brandManufacturerDisplay = if (deviceManufacturer == deviceBrand) {
            deviceBrand
        } else {
            "$deviceBrand ($deviceManufacturer)"
        }
        val versionName = getString(R.string.app_version)
        uiHelper.appendLog(textViewLog, "设备：$brandManufacturerDisplay | $deviceModel\n系统：Android $androidVersion | 安全补丁 $securityPatch\n上传器版本：$versionName")
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun requestFilePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "请为RotaenoUploader授予文件访问权限！", Toast.LENGTH_LONG).show()
                startActivityForResult(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), MANAGE_EXTERNAL_STORAGE_REQUEST_CODE)
            }
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION_REQUEST_CODE)
        }
    }

    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)}\n      with the appropriate {@link ActivityResultContract} and handling the result in the\n      {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data == null || resultCode != RESULT_OK) {
            if (requestCode == DATA_REQUEST_CODE) {
                Toast.makeText(this, "操作被取消", Toast.LENGTH_SHORT).show()
                uiHelper.hideLoading(progressBar)
            }
            return
        }
        if (requestCode == MANAGE_EXTERNAL_STORAGE_REQUEST_CODE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                Toast.makeText(this, "所有文件访问权限授予成功", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "未授予所有文件访问权限！", Toast.LENGTH_LONG).show()
            }
        } else if (requestCode == DATA_REQUEST_CODE) {
            handleDocumentResult(data)
        }
    }

    private fun getGameData(packageName: String) {
        val selectedMode = settingsPreferences.getString("selected_mode", null)

        when (selectedMode) {
            "traditional" -> {
                getGameDataByFile(packageName)
            }
            "saf" -> {
                getGameDataBySAF(packageName)
            }
            "shizuku" -> {
                checkShizukuStatus(this)
                if (Shizuku.pingBinder()) {
                    readUserDataWithShizuku(packageName)
                }
            }
            else -> {
                Toast.makeText(this, "未知的模式！", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getGameDataBySAF(packageName: String) {
        uiHelper.showLoading(progressBar)
        var processedPath = "Android%2Fdata%2F$packageName"
        val dataAccessBypass = settingsPreferences.getBoolean("data_access_bypass", false)
        if (dataAccessBypass) {
            Log.d(TAG, "已开启data绕过")
            processedPath = processedPath.replace("Android", "Andro\u200Bid")
        }
        Log.d(TAG, "SAF Path: $processedPath")
        val uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A$processedPath/document/primary%3A$processedPath")
        val intent = Intent("android.intent.action.OPEN_DOCUMENT_TREE")
        intent.putExtra("android.provider.extra.INITIAL_URI", uri)
        Toast.makeText(this, "请直接点击底部的\"使用此文件夹\"", Toast.LENGTH_SHORT).show()
        startActivityForResult(intent, DATA_REQUEST_CODE)
    }

    private fun handleDocumentResult(data: Intent) {
        val documentUri = data.data ?: return
        val (objectId, gameSaveData) = fileHelper.handleDocumentResult(documentUri)

        if (objectId == null) {
            uiHelper.appendLog(textViewLog, "失败：.userdata文件不存在或无法访问")
            uiHelper.hideLoading(progressBar)
            return
        }

        runOnUiThread { textViewObjectId.text = objectId }

        if (gameSaveData == null) {
            uiHelper.appendLog(textViewLog, "失败：GameSave文件不存在或无法访问")
            uiHelper.hideLoading(progressBar)
            return
        }

        val encodedGameSaveData = Base64.encodeToString(gameSaveData, Base64.DEFAULT)
        CoroutineScope(Dispatchers.IO).launch {
            postGameData(objectId, encodedGameSaveData)
        }
    }

    private fun getUploadUrl(): String {
        var url = settingsPreferences.getString("remote_server_address", "")
        if (url.isNullOrEmpty()) {
            url = "http://rotaeno.api.mihoyo.pw/decryptAndSaveGameData"
            uiHelper.appendLog(textViewLog, "正在使用默认服务器地址")
        }
        return url
    }

    private fun getGameDataByFile(packageName: String) {
        uiHelper.showLoading(progressBar)
        uiHelper.appendLog(textViewLog, "正在尝试获取游戏数据...")
        var processedPath = "Android/data/$packageName"
        val dataAccessBypass = settingsPreferences.getBoolean("data_access_bypass", false)
        if (dataAccessBypass) {
            Log.d("RotaenoUploader", "已开启data绕过")
            processedPath = processedPath.replace("Android", "Andro\u200Bid")
        }
        CoroutineScope(Dispatchers.IO).launch {
            val filePath = "/storage/emulated/0/$processedPath/files/RotaenoLC/.userdata"
            Log.d("RotaenoUploader", "File path: $filePath")
            val jsonObject = fileHelper.readUserData(filePath)

            if (jsonObject != null) {
                val objectId = jsonObject.get("objectId").asString
                runOnUiThread {
                    textViewObjectId.text = objectId
                }

                val gameSaveFileName = fileHelper.sha256ToHex("GameSave$objectId")
                val gameSaveFilePath = "/storage/emulated/0/$processedPath/files/$gameSaveFileName"
                Log.d("RotaenoUploader", "GameSave file path: $gameSaveFilePath")
                val gameSaveData = fileHelper.readGameSaveFile(gameSaveFilePath)

                if (gameSaveData != null) {
                    val encodedGameSaveData = Base64.encodeToString(gameSaveData, Base64.DEFAULT)
                    uiHelper.appendLog(textViewLog, "正在发送数据到服务器...")
                    postGameData(objectId, encodedGameSaveData)
                } else {
                    uiHelper.appendLog(textViewLog, "GameSave文件为空或无法读取")
                    uiHelper.hideLoading(progressBar)
                }
            } else {
                uiHelper.appendLog(textViewLog, "失败，文件不存在或无法访问")
                uiHelper.hideLoading(progressBar)
            }
        }
    }

    private fun postGameData(objectId: String, gameSaveData: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val delayedCheck = launch {
                delay(6000)
                uiHelper.appendLog(textViewLog, "目标服务器响应缓慢，仍在上传中...")
            }

            val url = getUploadUrl()

            if (!Patterns.WEB_URL.matcher(url).matches()) {
                uiHelper.showSnackBar("服务器地址无效")
                uiHelper.appendLog(textViewLog, "无效的服务器地址: $url")
                uiHelper.hideLoading(progressBar)
                delayedCheck.cancel()
                return@launch
            }

            val postResult = networkHelper.postGameData(url, objectId, gameSaveData)
            if (postResult.isSuccess) {
                uiHelper.appendLog(textViewLog, "上传成功！")

                val uploadCount = sharedPreferences.getLong(PREF_KEY_UPLOAD_COUNT, 0) + 1
                sharedPreferences.edit {
                    putLong(PREF_KEY_LAST_UPLOAD_TIME, System.currentTimeMillis())
                    putLong(PREF_KEY_UPLOAD_COUNT, uploadCount)
                }
                showLastUploadTime()
                if (uploadCount == 10L || uploadCount == 25L || uploadCount == 50L || uploadCount == 100L) {
                    runOnUiThread {
                        showStarDialog(uploadCount)
                    }
                }
//                uiHelper.appendLog(textViewLog, "RotaenoUploader已为您成功上传 $uploadCount 次")
            } else {
                uiHelper.appendLog(textViewLog, "发送数据失败: ${postResult.errorMessage}")
            }

            delayedCheck.cancel()
            uiHelper.hideLoading(progressBar)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Log.d(TAG, "onServiceConnected")
            iFileService = IFileService.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.d(TAG, "onServiceDisconnected")
            iFileService = null
        }
    }

    private fun initializeService() {
        USER_SERVICE_ARGS = Shizuku.UserServiceArgs(
            ComponentName(packageName, FileService::class.java.name)
        ).daemon(false).debuggable(BuildConfig.DEBUG).processNameSuffix("file_explorer_service").version(1)

        bindService()
    }

    private fun bindService() {
        Shizuku.bindUserService(USER_SERVICE_ARGS, serviceConnection)
    }

    private fun setupShizukuListeners() {
        // 监听 Shizuku 的权限变化
        Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE_SHIZUKU_PERMISSION && grantResult == PackageManager.PERMISSION_GRANTED) {
                initializeService()  // 用户授予权限后重新初始化服务
            }
        }

        // 监听 Shizuku 的启动状态
//        Shizuku.addBinderReceivedListener {
//            if (checkShizukuPermission()) {
//                initializeService()  // Shizuku 启动后重新初始化服务
//            }
//        }
    }

    private fun checkShizukuPermission(): Boolean {
        if (!Shizuku.pingBinder()) {
//            textViewFiles.text = "Shizuku is not running"
            return false
        }

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(REQUEST_CODE_SHIZUKU_PERMISSION)
            return false
        }

        return true
    }

    private fun readUserDataWithShizuku(packageName: String) {
        if (checkShizukuPermission()) {
            initializeService()
            uiHelper.showLoading(progressBar)
            fileExplorerService?.let {
                runCatching {
                    val fileContent = it.readFile("/storage/emulated/0/Android/data/$packageName/files/RotaenoLC/.userdata")
                    val jsonObject = JsonParser.parseString(fileContent).asJsonObject
                    val objectId = jsonObject.get("objectId").asString
                    runOnUiThread {
                        textViewObjectId.text = objectId
                    }
                    val gameSaveFileName = fileHelper.sha256ToHex("GameSave$objectId")
                    readGameSaveWithShizuku(packageName, gameSaveFileName, objectId)
                }.onFailure { e ->
                    uiHelper.appendLog(textViewLog, "读取文件失败: ${e.message}")
                    uiHelper.hideLoading(progressBar)
                }
            } ?: run {
                uiHelper.appendLog(textViewLog, "文件服务不可用，请再试一次或重启App")
                uiHelper.hideLoading(progressBar)
            }
        }
    }

    private fun readGameSaveWithShizuku(packageName: String, fileName: String, objectId: String) {
        val path = "/storage/emulated/0/Android/data/$packageName/files/$fileName"
        fileExplorerService?.let {
            runCatching {
                val fileContent = it.readBytesAndEncode(path)
                uiHelper.appendLog(textViewLog, "正在发送数据到服务器...")
                postGameData(objectId, fileContent)
            }.onFailure { e ->
                uiHelper.appendLog(textViewLog, "读取文件失败: ${e.message}")
                uiHelper.hideLoading(progressBar)
            }
        } ?: run {
            uiHelper.appendLog(textViewLog, "文件服务不可用，请再试一次或重启App")
            uiHelper.hideLoading(progressBar)
        }
    }

    private fun copyToClipboard(text: CharSequence) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("objectId", text)
        clipboard.setPrimaryClip(clip)
        uiHelper.showSnackBar("已复制ObjectId到剪切板")
    }

    private fun showStarDialog(uploadCount: Long) {
        MaterialAlertDialogBuilder(this)
            .setTitle("支持一下吧？")
            .setMessage("RotaenoUploader 已为你成功上传成绩 $uploadCount 次，愿意去 GitHub 点个 Star 吗？")
            .setPositiveButton("好") { _, _ ->
                // 打开 GitHub 链接
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/milkycandy/RotaenoUploader"))
                startActivity(browserIntent)
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
    private fun openShizukuApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (intent != null) {
            context.startActivity(intent)
        }
    }

    fun checkShizukuStatus(context: Context) {
        val isShizukuInstalled = try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

        if (!isShizukuInstalled) {
            MaterialAlertDialogBuilder(this)
                .setTitle("未安装Shizuku")
                .setMessage("似乎没有找到Shizuku，您需要安装Shizuku才能继续。")
                .setPositiveButton("下载Shizuku") { _, _ ->
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases"))
                    startActivity(browserIntent)
                }
                .setNegativeButton("取消") { _, _ ->
                }
                .show()
        } else if (!Shizuku.pingBinder()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Shizuku服务未启动")
                .setMessage("Shizuku服务似乎没有启动，请先启动Shizuku服务。")
                .setPositiveButton("打开Shizuku") { _, _ ->
                    openShizukuApp(this)
                }
                .setNegativeButton("取消") { _, _ ->
                }
                .show()
        }
    }

    companion object {
        private const val TAG = "RotaenoUploader"
        private const val PREF_KEY_LAST_UPLOAD_TIME = "last_upload_time"
        private const val PREF_KEY_UPLOAD_COUNT = "upload_count"
        private const val PREF_KEY_SELECTED_BUTTON = "selected_button"
        private const val PREFS_NAME = "RotaenoUploaderPrefs"
        private const val MANAGE_EXTERNAL_STORAGE_REQUEST_CODE = 514
        private const val STORAGE_PERMISSION_REQUEST_CODE = 114
        private const val DATA_REQUEST_CODE = 1919
        private const val REQUEST_CODE_SHIZUKU_PERMISSION = 1
    }
}