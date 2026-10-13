package com.petmatch.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petmatch.service.CleanupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calls the cleanup service directly (instead of waiting for 03:00) and checks the three rules.
 * Old data is faked by moving timestamps back with SQL, because the API cannot create expired posts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class CleanupIntegrationTest {

    private static final double BASE_LAT = 53.3498;
    private static final double BASE_LNG = -6.2603;

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private CleanupService cleanupService;
    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Object[]> created = new ArrayList<>();

    @Test
    void expiresOverduePosts_deletesLongExpiredOnesWithTheirMatches_andDeletesOldNotifications() throws Exception {
        OffsetDateTime t0 = OffsetDateTime.now(ZoneOffset.UTC).minusDays(20).truncatedTo(ChronoUnit.SECONDS);
        String ownerEmail = "cleanup-" + UUID.randomUUID() + "@example.com";
        String ownerToken = register(ownerEmail);
        String finderToken = register("cleanup-" + UUID.randomUUID() + "@example.com");

        try {
            // --- Setup 1: one overdue (still OPEN) post and one fresh post, same species, same type ---
            String species1 = "sp" + UUID.randomUUID().toString().substring(0, 10);
            long overdue = createPost(ownerToken, "LOST", species1, "orange", "medium", true, t0, 0.0);
            long fresh = createPost(ownerToken, "LOST", species1, "orange", "medium", true, t0, 0.0);
            jdbc.update("UPDATE pet_posts SET expires_at = now() - interval '1 day' WHERE id = ?", overdue);

            // --- Setup 2: a matched pair, then the lost post "expired 40 days ago" ---
            String species2 = "sp" + UUID.randomUUID().toString().substring(0, 10);
            long lost2 = createPost(ownerToken, "LOST", species2, "grey", "small", false, t0, 0.0);
            long seen2 = createPost(finderToken, "SIGHTING", species2, "grey", "small", false, t0.plusHours(1), 0.0018);
            waitForMatchCount(lost2, 1);
            jdbc.update("UPDATE pet_posts SET status = 'EXPIRED', expires_at = now() - interval '40 days' WHERE id = ?",
                    lost2);

            // --- Setup 3: one 100-day-old notification and one recent one ---
            Long ownerId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, ownerEmail);
            jdbc.update("INSERT INTO notifications (user_id, type, payload, created_at) "
                    + "VALUES (?, 'TEST_OLD', '{}'::jsonb, now() - interval '100 days')", ownerId);
            jdbc.update("INSERT INTO notifications (user_id, type, payload, created_at) "
                    + "VALUES (?, 'TEST_NEW', '{}'::jsonb, now() - interval '1 day')", ownerId);

            // --- Run the cleanup once ---
            CleanupService.Result result = cleanupService.runCleanup();
            assertThat(result.expiredPosts()).isGreaterThanOrEqualTo(1);
            assertThat(result.deletedPosts()).isGreaterThanOrEqualTo(1);
            assertThat(result.deletedNotifications()).isGreaterThanOrEqualTo(1);

            // 1. The overdue post is now EXPIRED, the fresh one is untouched, and the public list hides the expired one
            assertThat(statusOf(overdue)).isEqualTo("EXPIRED");
            assertThat(statusOf(fresh)).isEqualTo("OPEN");
            String publicList = restTemplate.getForEntity("/api/posts?species=" + species1, String.class).getBody();
            assertThat(publicList).contains("\"id\":" + fresh);
            assertThat(publicList).doesNotContain("\"id\":" + overdue);

            // 2. The long-expired post is gone for good, its match went with it, the other post stays
            assertThat(count("SELECT COUNT(*) FROM pet_posts WHERE id = ?", lost2)).isEqualTo(0);
            assertThat(count("SELECT COUNT(*) FROM matches WHERE lost_post_id = ?", lost2)).isEqualTo(0);
            assertThat(count("SELECT COUNT(*) FROM pet_posts WHERE id = ?", seen2)).isEqualTo(1);

            // 3. The old notification is gone, the recent one stays
            assertThat(count("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'TEST_OLD'", ownerId))
                    .isEqualTo(0);
            assertThat(count("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'TEST_NEW'", ownerId))
                    .isEqualTo(1);

            // Running it again changes nothing for our data (idempotent)
            cleanupService.runCleanup();
            assertThat(statusOf(overdue)).isEqualTo("EXPIRED");
            assertThat(statusOf(fresh)).isEqualTo("OPEN");
        } finally {
            cleanUp();
        }
    }

    // ---------- helpers ----------

    private String statusOf(long postId) {
        return jdbc.queryForObject("SELECT status FROM pet_posts WHERE id = ?", String.class, postId);
    }

    private int count(String sql, Object arg) {
        Integer n = jdbc.queryForObject(sql, Integer.class, arg);
        return n == null ? 0 : n;
    }

    /** Matching runs in the background, so poll for up to ~10 seconds. */
    private void waitForMatchCount(long lostId, int expected) throws InterruptedException {
        int last = -1;
        for (int i = 0; i < 50; i++) {
            last = count("SELECT COUNT(*) FROM matches WHERE lost_post_id = ?", lostId);
            if (last == expected) {
                return;
            }
            Thread.sleep(200);
        }
        assertThat(last).as("matches for lost post %d", lostId).isEqualTo(expected);
    }

    private long createPost(String token, String type, String species, String color, String size,
                            Boolean hasCollar, OffsetDateTime eventTime, double latOffset) throws Exception {
        String body = """
                {"type":"%s","species":"%s","color":"%s","size":"%s",
                 "hasCollar":%s,"description":"cleanup test",
                 "eventTime":"%s",
                 "latitude":%s,"longitude":%s,"publicArea":"Dublin 1"}
                """.formatted(type, species, color, size, hasCollar, eventTime,
                BASE_LAT + latOffset, BASE_LNG);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/posts", HttpMethod.POST, new HttpEntity<>(body, authHeaders(token)), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        long id = objectMapper.readTree(response.getBody()).get("id").asLong();
        created.add(new Object[]{id, token});
        return id;
    }

    /** Some posts are already deleted by the cleanup itself; deleting them again just returns an error we ignore. */
    private void cleanUp() {
        for (Object[] entry : created) {
            restTemplate.exchange("/api/posts/" + entry[0], HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders((String) entry[1])), String.class);
        }
        created.clear();
    }

    private String register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"password123","displayName":"Cleanup Test User"}
                """.formatted(email);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/register", new HttpEntity<>(body, jsonHeaders()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody()).get("token").asText();
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}