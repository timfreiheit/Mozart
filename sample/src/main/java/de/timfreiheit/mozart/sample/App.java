package de.timfreiheit.mozart.sample;

import android.app.Application;

import de.timfreiheit.mozart.Mozart;
import timber.log.Timber;

public class App extends Application {

    private static App app;

    @Override
    public void onCreate() {
        super.onCreate();
        app = this;

        Timber.plant(new Timber.DebugTree());
        ImageLoader.getInstance().init(this);

        Mozart.get(this).init();
    }

    public static App instance() {
        return app;
    }
}
