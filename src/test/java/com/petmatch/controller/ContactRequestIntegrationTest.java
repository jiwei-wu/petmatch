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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestRestTemplate
class ContactRequestIntegrationTest {

    private static final double BASE_LAT = 53.3498;
    private static final double BASE_LNG = -6.2603;

    @Autowired
    private TestRestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Object[]> created = new ArrayList<>();

    @Test
    void contactDetailsStayHiddenUntilAccepted_andOnlyTheRecipientDecides() throws Exception {
        OffsetDateTime t0 = OffsetDateTime.now(ZoneOffset.UTC).minusDays(20).truncatedTo(ChronoUnit.SECONDS);
        String ownerEmail = uniqueEmail();
        String finderEmail = uniqueEmail();
        String ownerToken = register(ownerEmail);
        String finderToken = register(finderEmail);
        String strangerToken = register(uniqueEmail());

        try {
            // ---------- Pair 1: request is sent and ACCEPTED ----------
            String species1 = "sp" + UUID.randomUUID().toString().substring(0, 10);
            long lost1 = createPost(ownerToken, "LOST", species1, "orange", "medium", true, t0, 0.0);
            long seen1 = createPost(finderToken, "SIGHTING", species1, "orange", "medium", true, t0.plusHours(1), 0.0018);
            long match1 = waitForMatchId(ownerToken, lost1);

            // Validation: blank message is rejected
            assertThat(sendRequest(match1, "   ", ownerToken).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            // A stranger cannot send a request for someone else's match
            assertThat(sendRequest(match1, "hello", strangerToken).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

            // The owner sends a request: created, pending, and no email in the response
            ResponseEntity<String> sent = sendRequest(match1, "I think that is my cat, can we talk?", ownerToken);
            assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode sentJson = objectMapper.readTree(sent.getBody());
            assertThat(sentJson.get("status").asText()).isEqualTo("PENDING");
            assertThat(sent.getBody()).doesNotContain("@example.com");
            long requestId = sentJson.get("id").asLong();

            // One request per sender per match; and the other side cannot send a second one in reverse
            assertThat(sendRequest(match1, "again", ownerToken).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(sendRequest(match1, "me too", finderToken).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

            // The recipient sees it as RECEIVED, with no email yet, and gets a notification
            ResponseEntity<String> finderListRaw = getRequests(finderToken);
            assertThat(finderListRaw.getBody()).doesNotContain("@example.com");
            JsonNode finderView = findById(finderListRaw.getBody(), requestId);
            assertThat(finderView.get("direction").asText()).isEqualTo("RECEIVED");
            assertThat(finderView.get("status").asText()).isEqualTo("PENDING");
            assertThat(finderView.get("message").asText()).isEqualTo("I think that is my cat, can we talk?");
            assertThat(finderView.get("contactEmail").isNull()).isTrue();
            assertThat(hasNotification(finderToken, "CONTACT_REQUEST", seen1)).isTrue();

            // The sender sees it as SENT, no email yet
            JsonNode ownerView = findById(getRequests(ownerToken).getBody(), requestId);
            assertThat(ownerView.get("direction").asText()).isEqualTo("SENT");
            assertThat(ownerView.get("contactEmail").isNull()).isTrue();

            // Only the recipient can decide: the sender and a stranger get 404
            assertThat(decide(requestId, "accept", ownerToken).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(decide(requestId, "accept", strangerToken).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

            // The recipient accepts; repeating is harmless; changing the decision is a conflict
            assertThat(decide(requestId, "accept", finderToken).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(decide(requestId, "accept", finderToken).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(decide(requestId, "decline", finderToken).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

            // Now, and only now, each side sees the other's email
            JsonNode ownerAfter = findById(getRequests(ownerToken).getBody(), requestId);
            JsonNode finderAfter = findById(getRequests(finderToken).getBody(), requestId);
            assertThat(ownerAfter.get("status").asText()).isEqualTo("ACCEPTED");
            assertThat(ownerAfter.get("contactEmail").asText()).isEqualTo(finderEmail);
            assertThat(finderAfter.get("contactEmail").asText()).isEqualTo(ownerEmail);
            assertThat(hasNotification(ownerToken, "CONTACT_ACCEPTED", lost1)).isTrue();

            // A stranger still sees nothing
            assertThat(objectMapper.readTree(getRequests(strangerToken).getBody())).isEmpty();

            // ---------- Pair 2: request is DECLINED ----------
            String species2 = "sp" + UUID.randomUUID().toString().substring(0, 10);
            long lost2 = createPost(ownerToken, "LOST", species2, "grey", "small", false, t0, 0.0);
            long seen2 = createPost(finderToken, "SIGHTING", species2, "grey", "small", false, t0.plusHours(2), 0.0018);
            long match2 = waitForMatchId(ownerToken, lost2);

            ResponseEntity<String> sent2 = sendRequest(match2, "Is this your cat?", finderToken);
            assertThat(sent2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            long requestId2 = objectMapper.readTree(sent2.getBody()).get("id").asLong();

            assertThat(decide(requestId2, "decline", ownerToken).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            JsonNode declined = findById(getRequests(finderToken).getBody(), requestId2);
            assertThat(declined.get("status").asText()).isEqualTo("DECLINED");
            assertThat(declined.get("contactEmail").isNull()).isTrue();      // never revealed after a decline
            assertThat(hasNotification(finderToken, "CONTACT_DECLINED", seen2)).isTrue();

            // After a decline the sender cannot ask again for the same match
            assertThat(sendRequest(match2, "please?", finderToken).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

            // No token, no access
            assertThat(restTemplate.getForEntity("/api/contact-requests", String.class).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        } finally {
            cleanUp();
        }
    }

    // ---------- helpers ----------

    private ResponseEntity<String> sendRequest(long matchId, String message, String token) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("message", message));
        return restTemplate.exchange("/api/matches/" + matchId + "/contact-requests", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)), String.class);
    }

    private ResponseEntity<String> getRequests(String token) {
        return restTemplate.exchange("/api/contact-requests", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), String.class);
    }

    private ResponseEntity<String> decide(long requestId, String action, String token) {
        return restTemplate.exchange("/api/contact-requests/" + requestId + "/" + action, HttpMethod.POST,
                new HttpEntity<>(authHeaders(token)), String.class);
    }

    private JsonNode findById(String json, long id) throws Exception {
        for (JsonNode node : objectMapper.readTree(json)) {
            if (node.get("id").asLong() == id) {
                return node;
            }
        }
        throw new AssertionError("Contact request " + id + " not found in " + json);
    }

    private boolean hasNotification(String token, String type, long myPostId) throws Exception {
        ResponseEntity<String> response = restTemplate.exchange("/api/notifications", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), String.class);
        for (JsonNode node : objectMapper.readTree(response.getBody())) {
            if (type.equals(node.get("type").asText()) && node.get("myPostId").asLong() == myPostId) {
                return true;
            }
        }
        return false;
    }

    /** Matching runs in the background, so poll for up to ~10 seconds for the match to appear. */
    private long waitForMatchId(String token, long myPostId) throws Exception {
        for (int i = 0; i < 50; i++) {
            ResponseEntity<String> response = restTemplate.exchange("/api/matches", HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            for (JsonNode node : objectMapper.readTree(response.getBody())) {
                if (node.get("myPostId").asLong() == myPostId) {
                    return node.get("matchId").asLong();
                }
            }
            Thread.sleep(200);
        }
        throw new AssertionError("No match appeared for post " + myPostId);
    }

    private long createPost(String token, String type, String species, String color, String size,
                            Boolean hasCollar, OffsetDateTime eventTime, double latOffset) throws Exception {
        String body = """
                {"type":"%s","species":"%s","color":"%s","size":"%s",
                 "hasCollar":%s,"description":"contact request test",
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

    private String uniqueEmail() {
        return "contact-" + UUID.randomUUID() + "@example.com";
    }

    private String register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"password123","displayName":"Contact Test User"}
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