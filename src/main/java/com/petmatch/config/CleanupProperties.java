package com.petmatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Data retention settings. Override in application.yaml with petmatch.cleanup.xxx.
 * (The schedule itself is petmatch.cleanup.cron, read by CleanupScheduler.)
 */
@Component
@ConfigurationProperties(prefix = "petmatch.cleanup")
public class CleanupProperties {

    /** Expired posts are deleted for good this many days after they expired. */
    private int expiredPostRetentionDays = 30;

    /** Notifications older than this many days are deleted. */
    private int notificationRetentionDays = 90;

    public int getExpiredPostRetentionDays() { return expiredPostRetentionDays; }
    public void setExpiredPostRetentionDays(int v) { this.expiredPostRetentionDays = v; }
    public int getNotificationRetentionDays() { return notificationRetentionDays; }
    public void setNotificationRetentionDays(int v) { this.notificationRetentionDays = v; }
}