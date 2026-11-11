package com.knowledgeforge.knowledgeforge.nodeversion;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every minute: closes the edit sessions whose time is up (domain spec 5.1). */
@Component
public class VersionSessionJob {
    private final NodeVersionService versions;

    public VersionSessionJob(NodeVersionService versions) {
        this.versions = versions;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void run() {
        versions.closeDueSessions();
    }
}
