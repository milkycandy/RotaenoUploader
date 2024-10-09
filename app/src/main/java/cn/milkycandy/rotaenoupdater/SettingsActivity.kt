package cn.milkycandy.rotaenoupdater

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.color.DynamicColors

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        // Ensure the window content fits the system windows
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Apply window insets to the main layout
        val mainLayout = findViewById<View>(R.id.settingsLayout)
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Set padding to handle system bars
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            updatePreferencesSummary()

            val selectedModePref: Preference? = findPreference("selected_mode")

            selectedModePref?.setOnPreferenceClickListener {
                val intent = Intent(requireContext(), WelcomeActivity::class.java)
                intent.putExtra("source", "SettingsActivity")
                startActivity(intent)
                true
            }
        }

        override fun onResume() {
            super.onResume()
            updatePreferencesSummary()
        }

        private fun updatePreferencesSummary() {
            val selectedModePref: Preference? = findPreference("selected_mode")
            val settingsPreferences =
                PreferenceManager.getDefaultSharedPreferences(requireContext())
            val selectedMode = settingsPreferences.getString("selected_mode", "未选择")
            selectedModePref?.summaryProvider = Preference.SummaryProvider<Preference> {
                val modeDescription = when (selectedMode) {
                    "traditional" -> "传统"
                    "saf" -> "SAF（安卓存储访问框架）"
                    "shizuku" -> "Shizuku"
                    else -> "未选择"
                }
                "当前模式：$modeDescription"
            }
            val bypassDataAccess: SwitchPreferenceCompat? = findPreference("data_access_bypass")
            val disableShizukuCheck: SwitchPreferenceCompat? = findPreference("disable_shizuku_check")
            if (settingsPreferences.getString("selected_mode", "未选择") == "shizuku") {
                bypassDataAccess?.isVisible = false
                disableShizukuCheck?.isVisible = true
            } else {
                bypassDataAccess?.isVisible = true
                disableShizukuCheck?.isVisible = false
            }


//            val switchPreference: SwitchPreferenceCompat? = findPreference("data_access_bypass")
//            switchPreference?.isVisible = selectedMode != "saf"
        }
    }
}