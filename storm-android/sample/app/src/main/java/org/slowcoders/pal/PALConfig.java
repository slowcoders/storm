package org.slowcoders.pal;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import org.slowcoders.io.util.NPAsyncScheduler;
import org.slowcoders.pal.PAL;
import org.slowcoders.pal.io.Storage;
import org.slowcoders.sample.android.BaseApplication;
import org.slowcoders.util.Debug;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class PALConfig implements PAL.Impl {

    @Override
    public Storage getStorage() {
        return new Storage() {
            @Override
            public InputStream openInputStream(URI contentUri) throws IOException {
                return null;
            }

            @Override
            public File getPreferenceDirectory() {
                return null;
            }

            @Override
            public String getDatabaseDirectory() {
                return BaseApplication.getContext().getExternalCacheDir() + "/sample";
            }

            @Override
            public File getDownloadDirectory() {
                return null;
            }

            @Override
            public File getCacheDirectory() {
                return null;
            }
        };
    }

    @Override
    public NPAsyncScheduler.Executor getAsyncExecutor() {
        return new AndroidAsyncExecutor();
    }

    @Override
    public boolean isDebugVerbose() {
        return false;
    }

    @Override
    public boolean isDebugMode() {
        return false;
    }

    public static class AndroidAsyncExecutor extends Handler implements NPAsyncScheduler.Executor {

        private static int EXECUTE_TASK = 1;

        public AndroidAsyncExecutor() {
            super(Looper.getMainLooper());
        }

        @Override
        public void triggerAsync() {
            if (!this.hasMessages(EXECUTE_TASK)) {
                this.sendEmptyMessage(EXECUTE_TASK);
            }
        }

        public void handleMessage(Message msg) {
            if (msg.what != EXECUTE_TASK) {
                return;
            }
            try {
                NPAsyncScheduler.executePendingTasks();
            } catch (Exception e) {
                Debug.ignoreException(e);
            }
        }

        @Override
        public boolean isInMainThread() {
            return (Looper.myLooper() == Looper.getMainLooper());
        }
    }
}
