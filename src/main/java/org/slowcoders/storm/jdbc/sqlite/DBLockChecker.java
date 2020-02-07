package org.slowcoders.storm.jdbc.sqlite;

public interface DBLockChecker {
    boolean isDBLockByCurrentThread();
    boolean isDBLockByOtherThreads();
}
