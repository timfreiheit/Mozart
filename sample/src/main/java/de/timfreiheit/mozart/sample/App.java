package de.timfreiheit.mozart.sample;

import android.app.Application;
import android.net.Uri;
import android.util.Log;

import com.squareup.picasso.Picasso;

import timber.log.Timber;

public class App extends Application {

    private static final String TAG = App.class.getSimpleName();
    private static App app;

    @Override
    public void onCreate() {
        super.onCreate();
        app = this;

        Picasso.setSingletonInstance(new Picasso.Builder(this).listener((picasso, uri, exception) -> {
            Log.d(TAG, "onImageLoadFailed() called with " + "picasso = [" + picasso + "], uri = [" + uri + "], exception = [" + exception + "]");
        }).build());

        Timber.plant(new Timber.DebugTree());
    }

    public static App instance() {
        return app;
    }
}
