package com.petmatch.service;

import com.petmatch.config.CleanupProperties;
import com.petmatch.repository.CleanupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CleanupService {

    public record Result(int expiredPosts, int deletedPosts, int deletedNotifications) {}

    private final CleanupRepository cleanupRepository;
    private final CleanupProperties properties;

    public CleanupService(CleanupRepository cleanupRepository, CleanupProperties properties) {
        this.cleanupRepository = cleanupRepository;
        this.properties = properties;
    }

    /**
     * Order matters: first mark overdue posts as expired, then delete the ones that have been
     * expired for long enough. All three steps share one transaction, so a failure rolls back everything.
     */
    @Transactional
    public Result runCleanup() {
        int expired = cleanupRepository.expireOverdueOpenPosts();
        int deletedPosts = cleanupRepository.deleteLongExpiredPosts(properties.getExpiredPostRetentionDays());
        int deletedNotifications = cleanupRepository.deleteOldNotifications(properties.getNotificationRetentionDays());
        return new Result(expired, deletedPosts, deletedNotifications);
    }
}