package org.slowcoders.sample.android;

import android.app.Application;
import android.content.Context;

import org.slowcoders.sample.SampleEnv;

public class BaseApplication extends Application {

    private static Application instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        SampleEnv.init();
    }

    public static Context getContext() {
        return instance.getApplicationContext();
    }
}
