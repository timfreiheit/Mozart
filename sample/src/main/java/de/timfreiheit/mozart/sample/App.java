package de.timfreiheit.mozart.sample;

import android.app.Application;

import timber.log.Timber;

public class App extends Application {

    private static App app;

    @Override
    public void onCreate() {
        super.onCreate();
        app = this;

        Timber.plant(new Timber.DebugTree());
        ImageLoader.getInstance().init(this);
    }

    public static App instance() {
        return app;
    }
}
