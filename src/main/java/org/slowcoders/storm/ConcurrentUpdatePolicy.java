package org.slowcoders.storm;

public enum ConcurrentUpdatePolicy {
    ErrorOnUpdateConflict,
    IgnoreVersionConflict
}
