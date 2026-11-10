package com.knowledgeforge.knowledgeforge.node;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

/** Deletes branches that have been in the trash for 30 days, every day at 03:00 UTC. */
@Component
public class TrashPurgeJob {
    private static final Logger log = LoggerFactory.getLogger(TrashPurgeJob.class);
    private final TrashService trash;

    public TrashPurgeJob(TrashService trash) {
        this.trash = trash;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void run() {
        int n = trash.purgeExpired(new Date(System.currentTimeMillis() - TrashService.RETENTION.toMillis()));
        if (n > 0) log.info("Purged {} trashed branches older than 30 days", n);
    }
}
