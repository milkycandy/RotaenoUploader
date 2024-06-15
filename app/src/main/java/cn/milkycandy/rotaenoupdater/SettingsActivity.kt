package cn.milkycandy.rotaenoupdater

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.color.DynamicColors

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        enableEdgeToEdge()
        setContentView(R.layout.settings_activity)
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

            val serverAddressPref: EditTextPreference? = findPreference("remote_server_address")
            val dataAccessBypassPref: SwitchPreferenceCompat? = findPreference("data_access_bypass")

            serverAddressPref?.setOnPreferenceChangeListener { preference, newValue ->
                true
            }

            dataAccessBypassPref?.setOnPreferenceChangeListener { preference, newValue ->
                true
            }
        }
    }
}