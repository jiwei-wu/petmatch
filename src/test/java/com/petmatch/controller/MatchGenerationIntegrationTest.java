package com.petmatch.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petmatch.service.MatchService;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class MatchGenerationIntegrationTest {

    private static final double BASE_LAT = 53.3498;
    private static final double BASE_LNG = -6.2603;

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private MatchService matchService;
    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Object[]> created = new ArrayList<>();

    @Test
    void storesOnlyStrongMatches_isIdempotent_andDeletingAPostRemovesItsMatches() throws Exception {
        String species = "sp" + UUID.randomUUID().toString().substring(0, 10);
        OffsetDateTime t0 = OffsetDateTime.now(ZoneOffset.UTC).minusDays(20).truncatedTo(ChronoUnit.SECONDS);
        String ownerToken = registerAndGetToken();
        String finderToken = registerAndGetToken();

        try {
            long lostId = createPost(ownerToken, "LOST", species, "orange", "medium", true, t0, 0.0);
            long near = createPost(finderToken, "SIGHTING", species, "orange", "medium", true, t0.plusHours(1), 0.0018);
            long mid = createPost(finderToken, "SIGHTING", species, "cream", "medium", false, t0.plusDays(1), 0.0135);
            // far/black/large/no collar/10 days later scores far below the threshold
            createPost(finderToken, "SIGHTING", species, "black", "large", false, t0.plusDays(10), 0.054);

            // First run creates the two strong matches only
            assertThat(matchService.generateMatchesFor(lostId)).isEqualTo(2);

            List<Long> sightingIds = jdbc.queryForList(
                    "SELECT sighting_post_id FROM matches WHERE lost_post_id = ?", Long.class, lostId);
            assertThat(sightingIds).containsExactlyInAnyOrder(near, mid);

            Double nearScore = jdbc.queryForObject(
                    "SELECT score FROM matches WHERE lost_post_id = ? AND sighting_post_id = ?",
                    Double.class, lostId, near);
            assertThat(nearScore).isGreaterThan(90.0);

            String breakdown = jdbc.queryForObject(
                    "SELECT score_breakdown::text FROM matches WHERE lost_post_id = ? AND sighting_post_id = ?",
                    String.class, lostId, near);
            assertThat(breakdown).contains("\"distance\"", "\"color\"");

            // Running again, from either side, creates nothing new
            assertThat(matchService.generateMatchesFor(lostId)).isEqualTo(0);
            assertThat(matchService.generateMatchesFor(near)).isEqualTo(0);
            assertThat(countMatchesForLost(lostId)).isEqualTo(2);

            // Deleting a post removes the matches that involve it
            ResponseEntity<String> deleted = restTemplate.exchange(
                    "/api/posts/" + near, HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders(finderToken)), String.class);
            assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(countMatchesForLost(lostId)).isEqualTo(1);
        } finally {
            cleanUp();
        }
    }

    // ---------- helpers ----------

    private int countMatchesForLost(long lostId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM matches WHERE lost_post_id = ?", Integer.class, lostId);
        return n == null ? 0 : n;
    }

    private long createPost(String token, String type, String species, String color, String size,
                            Boolean hasCollar, OffsetDateTime eventTime, double latOffset) throws Exception {
        String body = """
                {"type":"%s","species":"%s","color":"%s","size":"%s",
                 "hasCollar":%s,"description":"match generation test",
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

    private void cleanUp() {
        for (Object[] entry : created) {
            restTemplate.exchange("/api/posts/" + entry[0], HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders((String) entry[1])), String.class);
        }
        created.clear();
    }

    private String registerAndGetToken() throws Exception {
        String body = """
                {"email":"%s","password":"password123","displayName":"Match Test User"}
                """.formatted("match-" + UUID.randomUUID() + "@example.com");
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