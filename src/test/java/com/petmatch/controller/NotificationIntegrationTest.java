package com.petmatch.controller;

import com.petmatch.TestcontainersConfiguration;
import org.springframework.context.annotation.Import;
import com.fasterxml.jackson.databind.JsonNode;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestRestTemplate
class NotificationIntegrationTest {

    private static final double BASE_LAT = 53.3498;
    private static final double BASE_LNG = -6.2603;

    @Autowired
    private TestRestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Object[]> created = new ArrayList<>();

    @Test
    void newMatchNotifiesBothOwnersOnce_andOnlyOwnersCanReadThem() throws Exception {
        String species = "sp" + UUID.randomUUID().toString().substring(0, 10);
        OffsetDateTime t0 = OffsetDateTime.now(ZoneOffset.UTC).minusDays(20).truncatedTo(ChronoUnit.SECONDS);
        String ownerToken = registerAndGetToken();
        String finderToken = registerAndGetToken();

        try {
            long lostId = createPost(ownerToken, "LOST", species, "orange", "medium", true, t0, 0.0);
            long sightingId = createPost(finderToken, "SIGHTING", species, "orange", "medium", true,
                    t0.plusHours(1), 0.0018);

            // Matching runs in the background, so wait for each user's notification to show up
            JsonNode ownerList = waitForNotifications(ownerToken, 1);
            JsonNode finderList = waitForNotifications(finderToken, 1);

            JsonNode ownerNote = ownerList.get(0);
            JsonNode finderNote = finderList.get(0);

            assertThat(ownerNote.get("type").asText()).isEqualTo("NEW_MATCH");
            assertThat(ownerNote.get("myPostId").asLong()).isEqualTo(lostId);
            assertThat(finderNote.get("myPostId").asLong()).isEqualTo(sightingId);
            assertThat(ownerNote.get("matchId").asLong()).isEqualTo(finderNote.get("matchId").asLong());
            assertThat(ownerNote.get("score").asDouble()).isGreaterThan(90.0);
            assertThat(ownerNote.get("read").asBoolean()).isFalse();

            // Privacy: nothing identifying the other user, and no coordinates
            String body = getNotifications(ownerToken).getBody();
            assertThat(body).doesNotContain(
                    "\"userId\"", "\"email\"", "\"latitude\"", "\"longitude\"", "53.3498", "-6.2603");

            long ownerNoteId = ownerNote.get("id").asLong();

            // Someone else cannot mark it as read; it looks like it does not exist
            assertThat(markRead(ownerNoteId, finderToken).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

            // The owner can, and it is then reported as read
            assertThat(markRead(ownerNoteId, ownerToken).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(objectMapper.readTree(getNotifications(ownerToken).getBody()).get(0).get("read").asBoolean())
                    .isTrue();

            // Marking again is harmless
            assertThat(markRead(ownerNoteId, ownerToken).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            // No token, no access
            assertThat(restTemplate.getForEntity("/api/notifications", String.class).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        } finally {
            cleanUp();
        }
    }

    // ---------- helpers ----------

    /** Matching and notifying happen in the background, so poll for up to ~10 seconds. */
    private JsonNode waitForNotifications(String token, int expected) throws Exception {
        JsonNode last = null;
        for (int i = 0; i < 50; i++) {
            ResponseEntity<String> response = getNotifications(token);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            last = objectMapper.readTree(response.getBody());
            if (last.size() == expected) {
                return last;
            }
            Thread.sleep(200);
        }
        assertThat(last == null ? -1 : last.size()).as("notification count").isEqualTo(expected);
        return last;
    }

    private ResponseEntity<String> getNotifications(String token) {
        return restTemplate.exchange("/api/notifications", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), String.class);
    }

    private ResponseEntity<String> markRead(long id, String token) {
        return restTemplate.exchange("/api/notifications/" + id + "/read", HttpMethod.POST,
                new HttpEntity<>(authHeaders(token)), String.class);
    }

    private long createPost(String token, String type, String species, String color, String size,
                            Boolean hasCollar, OffsetDateTime eventTime, double latOffset) throws Exception {
        String body = """
                {"type":"%s","species":"%s","color":"%s","size":"%s",
                 "hasCollar":%s,"description":"notification test",
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
                {"email":"%s","password":"password123","displayName":"Notification Test User"}
                """.formatted("notify-" + UUID.randomUUID() + "@example.com");
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