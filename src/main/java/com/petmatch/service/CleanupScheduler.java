package com.petmatch.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(CleanupScheduler.class);

    private final CleanupService cleanupService;

    public CleanupScheduler(CleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    /** Every day at 03:00 by default; override with petmatch.cleanup.cron in application.yaml. */
    @Scheduled(cron = "${petmatch.cleanup.cron:0 0 3 * * *}")
    public void nightlyCleanup() {
        try {
            CleanupService.Result r = cleanupService.runCleanup();
            log.info("Nightly cleanup: {} post(s) expired, {} old post(s) deleted, {} old notification(s) deleted",
                    r.expiredPosts(), r.deletedPosts(), r.deletedNotifications());
        } catch (Exception e) {
            log.warn("Nightly cleanup failed", e);
        }
    }
}