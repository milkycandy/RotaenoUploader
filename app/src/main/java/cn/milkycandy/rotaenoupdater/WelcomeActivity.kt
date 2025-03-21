package cn.milkycandy.rotaenoupdater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import rikka.shizuku.Shizuku

class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_welcome)

        val settingsPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        // 检查是否已经写入过这个设置
        val isDataAccessBypassWritten =
            settingsPreferences.getBoolean("data_access_bypass_written", false)

        // 如果尚未写入过即为首次启动，如果系统版本是安卓13以上，则写入true（开启data限制绕过），并设置写入标记
        if (!isDataAccessBypassWritten && Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) {
            settingsPreferences.edit().putBoolean("data_access_bypass", true)
                .putBoolean("data_access_bypass_written", true).apply()
        }

        val modeSelectionGroup: RadioGroup = findViewById(R.id.modeSelectionRadioGroup)
        val finishFab: ExtendedFloatingActionButton = findViewById(R.id.finishFab)

        val selectedMode = settingsPreferences.getString("selected_mode", null)
        when (selectedMode) {
            "traditional" -> modeSelectionGroup.check(R.id.traditionalModeButton)
            "saf" -> modeSelectionGroup.check(R.id.safModeButton)
            "shizuku" -> modeSelectionGroup.check(R.id.shizukuModeButton)
        }

        val androidVersionTextView: TextView = findViewById(R.id.androidVersionTextView)
        androidVersionTextView.text = "您的设备运行Android ${Build.VERSION.RELEASE}"

//        modeSelectionGroup.setOnCheckedChangeListener { group, checkedId ->
//            when (checkedId) {
//                R.id.shizuku_mode -> {
//                    MaterialAlertDialogBuilder(this)
//                        .setTitle("提示")
//                        .setMessage("Shizuku模式是一个实验性功能，建议仅在其他模式均无法工作时使用。")
//                        .setPositiveButton("好") { _, _ ->
//                        }
//                        .show()
//                }
//            }
//        }

        finishFab.setOnClickListener {
            val selectedModeId = modeSelectionGroup.checkedRadioButtonId
            val selectedMode = findViewById<RadioButton>(selectedModeId)
            val source = intent.getStringExtra("source")

            when (selectedMode?.id) {
                R.id.traditionalModeButton -> {
                    settingsPreferences.edit().putString("selected_mode", "traditional").apply()
                    if (source == "SettingsActivity") {
                        requestPermissions()
                    }
                }

                R.id.safModeButton -> {
                    settingsPreferences.edit().putString("selected_mode", "saf").apply()
                }

                R.id.shizukuModeButton -> {
                    checkShizukuPermission()
                    settingsPreferences.edit().putString("selected_mode", "shizuku").apply()
                }

                else -> {
                    Toast.makeText(this, "请选择一个模式", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            if (source == "MainActivity") {
                val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                sharedPreferences.edit().putBoolean("is_first_run", false).apply()
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                finish()
            }
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "请为RotaenoUploader授予文件访问权限！", Toast.LENGTH_LONG)
                    .show()
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
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

    companion object {
        private const val PREFS_NAME = "RotaenoUploaderPrefs"
        private const val STORAGE_PERMISSION_REQUEST_CODE = 114
        private const val REQUEST_CODE_SHIZUKU_PERMISSION = 1
    }

    private fun checkShizukuPermission(): Boolean {
        if (!Shizuku.pingBinder()) {
            return false
        }

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(REQUEST_CODE_SHIZUKU_PERMISSION)
            return false
        }

        return true
    }

}
