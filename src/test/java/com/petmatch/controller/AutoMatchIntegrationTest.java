package com.petmatch.controller;

import com.petmatch.TestcontainersConfiguration;
import org.springframework.context.annotation.Import;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/** Creating posts through the API should produce matches in the background, with no manual call. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestRestTemplate
class AutoMatchIntegrationTest {

    private static final double BASE_LAT = 53.3498;
    private static final double BASE_LNG = -6.2603;

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Object[]> created = new ArrayList<>();

    @Test
    void creatingAMatchingSightingProducesAMatchAutomatically() throws Exception {
        String species = "sp" + UUID.randomUUID().toString().substring(0, 10);
        OffsetDateTime t0 = OffsetDateTime.now(ZoneOffset.UTC).minusDays(20).truncatedTo(ChronoUnit.SECONDS);
        String ownerToken = registerAndGetToken();
        String finderToken = registerAndGetToken();

        try {
            long lostId = createPost(ownerToken, "LOST", species, "orange", "medium", true, t0, 0.0);

            // A weak sighting first (far, different look, much later): must not produce a match
            createPost(finderToken, "SIGHTING", species, "black", "large", false, t0.plusDays(10), 0.054);
            // Then a strong one: should be matched by the background listener
            long strong = createPost(finderToken, "SIGHTING", species, "orange", "medium", true, t0.plusHours(1), 0.0018);

            waitForMatchCount(lostId, 1);

            List<Long> sightingIds = jdbc.queryForList(
                    "SELECT sighting_post_id FROM matches WHERE lost_post_id = ?", Long.class, lostId);
            assertThat(sightingIds).containsExactly(strong);
        } finally {
            cleanUp();
        }
    }

    // ---------- helpers ----------

    /** The listener runs asynchronously, so poll for up to ~10 seconds instead of asserting immediately. */
    private void waitForMatchCount(long lostId, int expected) throws InterruptedException {
        int last = -1;
        for (int i = 0; i < 50; i++) {
            Integer n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM matches WHERE lost_post_id = ?", Integer.class, lostId);
            last = n == null ? 0 : n;
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
                 "hasCollar":%s,"description":"auto match test",
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
                {"email":"%s","password":"password123","displayName":"Auto Match Test User"}
                """.formatted("auto-" + UUID.randomUUID() + "@example.com");
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