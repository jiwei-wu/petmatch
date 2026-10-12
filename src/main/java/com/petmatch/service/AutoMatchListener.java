package com.petmatch.service;

import com.petmatch.matching.PetPostCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AutoMatchListener {

    private static final Logger log = LoggerFactory.getLogger(AutoMatchListener.class);

    private final MatchService matchService;

    public AutoMatchListener(MatchService matchService) {
        this.matchService = matchService;
    }

    /**
     * Runs on a background thread. A failure here must never affect the post that was just
     * created, so every exception is caught and logged instead of propagated.
     */
    @Async
    @EventListener
    public void onPostCreated(PetPostCreatedEvent event) {
        try {
            int created = matchService.generateMatchesFor(event.postId());
            log.info("Auto-match for post {} created {} new match(es)", event.postId(), created);
        } catch (Exception e) {
            log.warn("Auto-match failed for post {}", event.postId(), e);
        }
    }
}