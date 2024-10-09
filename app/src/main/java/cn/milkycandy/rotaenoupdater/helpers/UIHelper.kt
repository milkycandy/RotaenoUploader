package cn.milkycandy.rotaenoupdater.helpers

import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar

class UIHelper(private val activity: AppCompatActivity) {
    fun appendLog(textView: TextView, message: String, addNewLine: Boolean = true) {
        activity.runOnUiThread {
            if (addNewLine) {
                textView.append("$message\n")
            } else {
                textView.append(message)
            }
        }
    }

    fun showSnackBar(message: String) {
        activity.runOnUiThread {
            Snackbar.make(activity.findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show()
        }
    }

    fun showLoading(progressBar: ProgressBar) {
        activity.runOnUiThread {
            progressBar.visibility = View.VISIBLE
        }
    }

    fun hideLoading(progressBar: ProgressBar) {
        activity.runOnUiThread {
            progressBar.visibility = View.GONE
        }
    }
}