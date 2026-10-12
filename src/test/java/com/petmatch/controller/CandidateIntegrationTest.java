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

/**
 * End-to-end test of GET /api/matching/posts/{id}/candidates.
 * A random species per test isolates it from other data in the shared dev database.
 *
 * Geometry: one degree of latitude is about 111 km, so
 *   +0.0018 deg is about 200 m, +0.0135 deg about 1.5 km, +0.054 deg about 6 km, +0.135 deg about 15 km.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class CandidateIntegrationTest {

    private static final double BASE_LAT = 53.3498;
    private static final double BASE_LNG = -6.2603;

    @Autowired
    private TestRestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // (postId, ownerToken) pairs so we can clean up after the test
    private final List<Object[]> created = new ArrayList<>();

    @Test
    void ranksCandidatesByScore_appliesHardFilters_andHidesLocation() throws Exception {
        String species = "sp" + UUID.randomUUID().toString().substring(0, 10);
        OffsetDateTime t0 = OffsetDateTime.now(ZoneOffset.UTC).minusDays(20).truncatedTo(ChronoUnit.SECONDS);

        String ownerToken = registerAndGetToken();
        String finderToken = registerAndGetToken();

        try {
            long lostId = createPost(ownerToken, "LOST", species, "orange", "medium", true, t0, 0.0);

            // Should be returned, best to worst
            long near = createPost(finderToken, "SIGHTING", species, "orange", "medium", true, t0.plusHours(1), 0.0018);
            long mid = createPost(finderToken, "SIGHTING", species, "cream", "medium", false, t0.plusDays(1), 0.0135);
            long far = createPost(finderToken, "SIGHTING", species, "black", "large", false, t0.plusDays(10), 0.054);

            // Must be filtered out by the hard filters
            createPost(finderToken, "SIGHTING", species, "orange", "medium", true, t0.plusHours(1), 0.135);       // 15 km: too far
            createPost(finderToken, "SIGHTING", "other" + species, "orange", "medium", true, t0.plusHours(1), 0.0018); // other species
            createPost(finderToken, "SIGHTING", species, "orange", "medium", true, t0.minusDays(1), 0.0018);        // seen before it got lost
            createPost(ownerToken, "SIGHTING", species, "orange", "medium", true, t0.plusHours(1), 0.0018);        // own post

            ResponseEntity<String> response = getCandidates(lostId, ownerToken);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode list = objectMapper.readTree(response.getBody());
            assertThat(list).hasSize(3);

            // Order: near > mid > far
            assertThat(list.get(0).get("postId").asLong()).isEqualTo(near);
            assertThat(list.get(1).get("postId").asLong()).isEqualTo(mid);
            assertThat(list.get(2).get("postId").asLong()).isEqualTo(far);
            assertThat(list.get(0).get("score").asDouble()).isGreaterThan(list.get(1).get("score").asDouble());
            assertThat(list.get(1).get("score").asDouble()).isGreaterThan(list.get(2).get("score").asDouble());

            // Only coarse distance bands are exposed
            assertThat(list.get(0).get("distanceBucket").asText()).isEqualTo("under 500 m");
            assertThat(list.get(1).get("distanceBucket").asText()).isEqualTo("1 - 5 km");
            assertThat(list.get(2).get("distanceBucket").asText()).isEqualTo("5 - 10 km");

            // Score breakdown is present
            assertThat(list.get(0).get("breakdown").has("distance")).isTrue();
            assertThat(list.get(0).get("breakdown").has("color")).isTrue();

            // Privacy: no coordinates, no user ids, no exact distance
            assertThat(response.getBody()).doesNotContain(
                    "\"latitude\"", "\"longitude\"", "\"exactLocation\"", "\"userId\"",
                    "\"microchipHash\"", "\"distanceMeters\"");
            assertThat(response.getBody()).doesNotContain("53.3498", "-6.2603", "53.3516");

            // The finder, looking from the sighting side, sees the lost post
            ResponseEntity<String> reverse = getCandidates(near, finderToken);
            assertThat(reverse.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode reverseList = objectMapper.readTree(reverse.getBody());
            assertThat(reverseList).hasSize(1);
            assertThat(reverseList.get(0).get("postId").asLong()).isEqualTo(lostId);
        } finally {
            cleanUp();
        }
    }

    @Test
    void onlyTheOwnerCanSeeCandidates_andMissingPostsReturn404() throws Exception {
        String species = "sp" + UUID.randomUUID().toString().substring(0, 10);
        OffsetDateTime t0 = OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).truncatedTo(ChronoUnit.SECONDS);
        String ownerToken = registerAndGetToken();
        String strangerToken = registerAndGetToken();

        try {
            long lostId = createPost(ownerToken, "LOST", species, "grey", "small", null, t0, 0.0);

            assertThat(getCandidates(lostId, strangerToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(getCandidates(999_999_999L, ownerToken).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

            ResponseEntity<String> noToken = restTemplate.getForEntity(
                    "/api/matching/posts/" + lostId + "/candidates", String.class);
            assertThat(noToken.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

            // Owner with no candidates gets an empty list, not an error
            ResponseEntity<String> empty = getCandidates(lostId, ownerToken);
            assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(objectMapper.readTree(empty.getBody())).isEmpty();
        } finally {
            cleanUp();
        }
    }

    // ---------- helpers ----------

    private long createPost(String token, String type, String species, String color, String size,
                            Boolean hasCollar, OffsetDateTime eventTime, double latOffset) throws Exception {
        String collar = hasCollar == null ? "null" : hasCollar.toString();
        String body = """
                {"type":"%s","species":"%s","color":"%s","size":"%s",
                 "hasCollar":%s,"description":"candidate test",
                 "eventTime":"%s",
                 "latitude":%s,"longitude":%s,"publicArea":"Dublin 1"}
                """.formatted(type, species, color, size, collar, eventTime,
                BASE_LAT + latOffset, BASE_LNG);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/posts", HttpMethod.POST, new HttpEntity<>(body, authHeaders(token)), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        long id = objectMapper.readTree(response.getBody()).get("id").asLong();
        created.add(new Object[]{id, token});
        return id;
    }

    private ResponseEntity<String> getCandidates(long postId, String token) {
        return restTemplate.exchange(
                "/api/matching/posts/" + postId + "/candidates",
                HttpMethod.GET, new HttpEntity<>(authHeaders(token)), String.class);
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
                {"email":"%s","password":"password123","displayName":"Candidate Test User"}
                """.formatted("candidate-" + UUID.randomUUID() + "@example.com");
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