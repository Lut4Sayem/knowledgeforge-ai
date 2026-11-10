package com.knowledgeforge.knowledgeforge.nodecontent;

/** Save based on an old revision (409). */
public class StaleRevisionException extends RuntimeException {
    private final long currentRevision;

    public StaleRevisionException(long currentRevision) {
        super("This page changed somewhere else");
        this.currentRevision = currentRevision;
    }

    public long getCurrentRevision() {
        return currentRevision;
    }
}
