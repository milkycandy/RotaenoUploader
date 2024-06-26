package cn.milkycandy.rotaenoupdater

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

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

        // 如果尚未写入过即为首次启动，如果系统版本是安卓11及以上，则写入true（开启data限制绕过），并设置写入标记
        if (!isDataAccessBypassWritten && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            settingsPreferences.edit().putBoolean("data_access_bypass", true)
                .putBoolean("data_access_bypass_written", true).apply()
        }

        val modeSelectionGroup: RadioGroup = findViewById(R.id.mode_selection_group)
        val finishFab: ExtendedFloatingActionButton = findViewById(R.id.finish_fab)

        val selectedMode = settingsPreferences.getString("selected_mode", null)
        when (selectedMode) {
            "traditional" -> modeSelectionGroup.check(R.id.traditional_mode)
            "saf" -> modeSelectionGroup.check(R.id.saf_mode)
        }

        finishFab.setOnClickListener {
            val selectedModeId = modeSelectionGroup.checkedRadioButtonId
            val selectedMode = findViewById<RadioButton>(selectedModeId)
            val source = intent.getStringExtra("source")

            // Perform actions based on the selected mode
            when (selectedMode?.id) {
                R.id.traditional_mode -> {
                    settingsPreferences.edit().putString("selected_mode", "traditional").apply()
                    if (source == "SettingsActivity") {
                        requestPermissions()
                    }
                }

                R.id.saf_mode -> {
                    settingsPreferences.edit().putString("selected_mode", "saf").apply()
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
    }
}
