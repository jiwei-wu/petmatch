package com.petmatch.controller;

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
@AutoConfigureTestRestTemplate
class MatchApiIntegrationTest {

    private static final double BASE_LAT = 53.3498;
    private static final double BASE_LNG = -6.2603;

    @Autowired
    private TestRestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Object[]> created = new ArrayList<>();

    @Test
    void listsMyMatches_hidesPrivateData_andLetsParticipantsConfirmOrDismiss() throws Exception {
        OffsetDateTime t0 = OffsetDateTime.now(ZoneOffset.UTC).minusDays(20).truncatedTo(ChronoUnit.SECONDS);
        String ownerToken = registerAndGetToken();
        String finderToken = registerAndGetToken();
        String strangerToken = registerAndGetToken();

        try {
            // Pair 1 (will be confirmed)
            String species1 = "sp" + UUID.randomUUID().toString().substring(0, 10);
            long lost1 = createPost(ownerToken, "LOST", species1, "orange", "medium", true, t0, 0.0);
            long seen1 = createPost(finderToken, "SIGHTING", species1, "orange", "medium", true, t0.plusHours(1), 0.0018);

            JsonNode ownerView = waitForMatchWithMyPost(ownerToken, lost1);
            JsonNode finderView = waitForMatchWithMyPost(finderToken, seen1);

            // Each side sees its own post as "mine" and the other as "other"
            assertThat(ownerView.get("myPostType").asText()).isEqualTo("LOST");
            assertThat(ownerView.get("otherPostId").asLong()).isEqualTo(seen1);
            assertThat(finderView.get("myPostType").asText()).isEqualTo("SIGHTING");
            assertThat(finderView.get("otherPostId").asLong()).isEqualTo(lost1);
            assertThat(ownerView.get("matchId").asLong()).isEqualTo(finderView.get("matchId").asLong());
            assertThat(ownerView.get("status").asText()).isEqualTo("SUGGESTED");
            assertThat(ownerView.get("score").asDouble()).isGreaterThan(90.0);
            assertThat(ownerView.get("breakdown").has("distance")).isTrue();
            assertThat(ownerView.get("breakdown").has("collar")).isTrue();

            // Privacy: nothing about the other user, and no coordinates
            String rawList = getMatches(ownerToken).getBody();
            assertThat(rawList).doesNotContain(
                    "\"userId\"", "\"email\"", "\"latitude\"", "\"longitude\"", "53.3498", "53.3516", "-6.2603");

            // A stranger sees nothing, and cannot act on someone else's match
            assertThat(objectMapper.readTree(getMatches(strangerToken).getBody())).isEmpty();
            long match1 = ownerView.get("matchId").asLong();
            assertThat(decide(match1, "confirm", strangerToken).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

            // Owner confirms; repeating is harmless; changing a decided match is a conflict
            assertThat(decide(match1, "confirm", ownerToken).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(waitForMatchWithMyPost(ownerToken, lost1).get("status").asText()).isEqualTo("CONFIRMED");
            assertThat(decide(match1, "confirm", ownerToken).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(decide(match1, "dismiss", finderToken).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

            // Pair 2 (will be dismissed by the finder, then hidden from both)
            String species2 = "sp" + UUID.randomUUID().toString().substring(0, 10);
            long lost2 = createPost(ownerToken, "LOST", species2, "grey", "small", false, t0, 0.0);
            long seen2 = createPost(finderToken, "SIGHTING", species2, "grey", "small", false, t0.plusHours(2), 0.0018);

            long match2 = waitForMatchWithMyPost(finderToken, seen2).get("matchId").asLong();
            assertThat(decide(match2, "dismiss", finderToken).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(hasMatchWithMyPost(getMatches(finderToken).getBody(), seen2)).isFalse();
            assertThat(hasMatchWithMyPost(getMatches(ownerToken).getBody(), lost2)).isFalse();

            // No token, no access
            assertThat(restTemplate.getForEntity("/api/matches", String.class).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        } finally {
            cleanUp();
        }
    }

    // ---------- helpers ----------

    /** Matching runs in the background, so poll for up to ~10 seconds for the match to appear. */
    private JsonNode waitForMatchWithMyPost(String token, long myPostId) throws Exception {
        for (int i = 0; i < 50; i++) {
            ResponseEntity<String> response = getMatches(token);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            for (JsonNode node : objectMapper.readTree(response.getBody())) {
                if (node.get("myPostId").asLong() == myPostId) {
                    return node;
                }
            }
            Thread.sleep(200);
        }
        throw new AssertionError("No match appeared for post " + myPostId);
    }

    private boolean hasMatchWithMyPost(String json, long myPostId) throws Exception {
        for (JsonNode node : objectMapper.readTree(json)) {
            if (node.get("myPostId").asLong() == myPostId) {
                return true;
            }
        }
        return false;
    }

    private ResponseEntity<String> getMatches(String token) {
        return restTemplate.exchange("/api/matches", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), String.class);
    }

    private ResponseEntity<String> decide(long matchId, String action, String token) {
        return restTemplate.exchange("/api/matches/" + matchId + "/" + action, HttpMethod.POST,
                new HttpEntity<>(authHeaders(token)), String.class);
    }

    private long createPost(String token, String type, String species, String color, String size,
                            Boolean hasCollar, OffsetDateTime eventTime, double latOffset) throws Exception {
        String body = """
                {"type":"%s","species":"%s","color":"%s","size":"%s",
                 "hasCollar":%s,"description":"match api test",
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
                {"email":"%s","password":"password123","displayName":"Match Api Test User"}
                """.formatted("matchapi-" + UUID.randomUUID() + "@example.com");
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