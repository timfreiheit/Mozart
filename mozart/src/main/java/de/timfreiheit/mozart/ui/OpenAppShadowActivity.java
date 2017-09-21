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

    public static final String EXTRA_NEXT_INTENT = "EXTRA_NEXT_INTENT";

    public static Intent newIntent(Context context) {
        return new Intent(context.getApplicationContext(), OpenAppShadowActivity.class);
    }

    public static Intent newIntent(Context context, Intent nextIntent) {
        Intent intent = new Intent(context.getApplicationContext(), OpenAppShadowActivity.class);
        intent.putExtra(EXTRA_NEXT_INTENT, nextIntent);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent nextIntent = getIntent().getParcelableExtra(EXTRA_NEXT_INTENT);

        if (!isTaskRoot()) {
            if (nextIntent != null) {
                startActivity(nextIntent);
            }
            // app task already exists
            finish();
            return;
        }

        Intent intent = getLauncherIntent(this);
        if (intent != null) {
            startActivity(intent);
        }
        if (nextIntent != null) {
            startActivity(nextIntent);
        }
        finish();
    }

    protected Intent getLauncherIntent(Context context) {
        String packageName = context.getPackageName();
        return context.getPackageManager().getLaunchIntentForPackage(packageName);
    }

}
