package de.timfreiheit.mozart.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;

/**
 * Shadow activity which just opens the app where the user left it
 * or restart the app using the default activity
 */
public class OpenAppShadowActivity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isTaskRoot()) {
            // app task already exists
            finish();
            return;
        }

        Intent intent = getLauncherIntent(this);
        if (intent != null) {
            startActivity(intent);
        }
        finish();
    }

    protected Intent getLauncherIntent(Context context) {
        String packageName = context.getPackageName();
        return context.getPackageManager().getLaunchIntentForPackage(packageName);
    }

}
