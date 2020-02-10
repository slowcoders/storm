package org.slowcoders.pal;

import org.slowcoders.io.util.NPAsyncScheduler;
import org.slowcoders.pal.io.Storage;
import org.slowcoders.util.Debug;

import java.awt.*;
import java.io.*;
import java.net.URI;

public class StormConfig implements PAL.Impl {

    @Override
    public Storage getStorage() {
        return DefaultStorage.instance;
    }

    @Override
    public NPAsyncScheduler.Executor getAsyncExecutor() {
        return DefaultExecutor.instance;
    }

    @Override
    public boolean isDebugVerbose() {
        return false;
    }

    @Override
    public boolean isDebugMode() {
        return false;
    }

    private static class DefaultExecutor implements NPAsyncScheduler.Executor, Runnable{

        private static DefaultExecutor instance = new DefaultExecutor();

        @Override
        public void run() {
            try {
                NPAsyncScheduler.executePendingTasks();
            }
            catch (Exception e) {
                Debug.wtf(e);
            }
        }
        @Override
        public void triggerAsync() {
            EventQueue.invokeLater(this);
        }

        @Override
        public boolean isInMainThread() {
            return EventQueue.isDispatchThread();
        }
    }

    private static class DefaultStorage implements Storage {

        private static DefaultStorage instance = new DefaultStorage();

        String dbDir = System.getProperty("user.home") + "/storm-dir";
        File settingsDir = new File(dbDir);
        File downloadDir = new File(dbDir + "/Downloads");
        File cacheDir = new File(dbDir + "/Cache");

        public DefaultStorage() {
            settingsDir.mkdirs();
            downloadDir.mkdirs();
            cacheDir.mkdirs();
        }

        public InputStream openInputStream(URI contentUri) throws IOException {
            File file;
            if (contentUri.getScheme() == null || !contentUri.getScheme().startsWith("file")) {
                //throw NPDebug.wtf("Only support file schema");
                file = new File(contentUri.toString());
            }
            else {
                file = new File(contentUri);
            }

            return new FileInputStream(file);
        }

        @Override
        public File getPreferenceDirectory() {
            return settingsDir;
        }

        @Override
        public String getDatabaseDirectory() {
            return dbDir;
        }

        @Override
        public File getDownloadDirectory() {
            return downloadDir;
        }

        @Override
        public File getCacheDirectory() {
            return cacheDir;
        }
    }
}
