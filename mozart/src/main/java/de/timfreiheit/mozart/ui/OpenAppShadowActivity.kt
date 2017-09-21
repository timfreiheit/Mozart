package de.timfreiheit.mozart.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Shadow activity which just opens the app where the user left it
 * or restart the app using the default activity
 */
class OpenAppShadowActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val nextIntent = intent.getParcelableExtra<Intent>(EXTRA_NEXT_INTENT)

        if (!isTaskRoot) {
            if (nextIntent != null) {
                startActivity(nextIntent)
            }
            // app task already exists
            finish()
            return
        }

        val intent = getLauncherIntent(this)
        if (intent != null) {
            startActivity(intent)
        }
        if (nextIntent != null) {
            startActivity(nextIntent)
        }
        finish()
    }

    private fun getLauncherIntent(context: Context): Intent? {
        val packageName = context.packageName
        return context.packageManager.getLaunchIntentForPackage(packageName)
    }

    companion object {

        val EXTRA_NEXT_INTENT = "EXTRA_NEXT_INTENT"

        fun newIntent(context: Context): Intent {
            return Intent(context.applicationContext, OpenAppShadowActivity::class.java)
        }

        fun newIntent(context: Context, nextIntent: Intent): Intent {
            val intent = Intent(context.applicationContext, OpenAppShadowActivity::class.java)
            intent.putExtra(EXTRA_NEXT_INTENT, nextIntent)
            return intent
        }
    }

}
