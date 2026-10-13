package com.petmatch.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled so the nightly cleanup can run. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}