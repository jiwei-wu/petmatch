package com.petmatch.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Turns on @Async so slow work (matching) runs on a background thread, not in the request. */
@Configuration
@EnableAsync
public class AsyncConfig {
}