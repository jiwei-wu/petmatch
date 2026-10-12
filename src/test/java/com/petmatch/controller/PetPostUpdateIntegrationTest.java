package com.petmatch.controller;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class PetPostUpdateIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void ownerCanUpdateDetails_andResponseStaysPrivate() throws Exception {
        String token = registerAndGetToken();
        long id = createPost(token, randomSpecies());

        try {
            ResponseEntity<String> updated = put(id, token, """
                    {"color":"ginger","description":"seen near the park","hasCollar":false}
                    """);
            assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(updated.getBody()).contains("ginger", "seen near the park");
            assertThat(updated.getBody()).doesNotContain(
                    "\"latitude\"", "\"longitude\"", "\"exactLocation\"", "\"userId\"", "53.3498", "-6.2603");

            ResponseEntity<String> detail = restTemplate.getForEntity("/api/posts/" + id, String.class);
            assertThat(detail.getBody()).contains("ginger", "seen near the park");
            assertThat(detail.getBody()).contains("\"hasCollar\":false");
        } finally {
            deletePost(id, token);
        }
    }

    @Test
    void otherUsersAndAnonymousCannotUpdate() throws Exception {
        String ownerToken = registerAndGetToken();
        String otherToken = registerAndGetToken();
        long id = createPost(ownerToken, randomSpecies());

        try {
            ResponseEntity<String> byOther = put(id, otherToken, "{\"description\":\"hacked\"}");
            assertThat(byOther.getStatusCode().is4xxClientError()).isTrue();

            ResponseEntity<String> anonymous = restTemplate.exchange(
                    "/api/posts/" + id, HttpMethod.PUT,
                    new HttpEntity<>("{\"description\":\"hacked\"}", jsonHeaders()), String.class);
            assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

            ResponseEntity<String> detail = restTemplate.getForEntity("/api/posts/" + id, String.class);
            assertThat(detail.getBody()).doesNotContain("hacked");
        } finally {
            deletePost(id, ownerToken);
        }
    }

    @Test
    void ownerCanMarkResolved_andPostLeavesOpenList() throws Exception {
        String token = registerAndGetToken();
        String species = randomSpecies();
        long id = createPost(token, species);

        try {
            assertThat(totalElements("/api/posts?species=" + species)).isEqualTo(1);

            ResponseEntity<String> resolved = put(id, token, "{\"status\":\"RESOLVED\"}");
            assertThat(resolved.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resolved.getBody()).contains("\"status\":\"RESOLVED\"");

            assertThat(totalElements("/api/posts?species=" + species)).isEqualTo(0);

            ResponseEntity<String> detail = restTemplate.getForEntity("/api/posts/" + id, String.class);
            assertThat(detail.getBody()).contains("\"status\":\"RESOLVED\"");
        } finally {
            deletePost(id, token);
        }
    }

    @Test
    void invalidUpdatesAreRejected() throws Exception {
        String token = registerAndGetToken();
        long id = createPost(token, randomSpecies());

        try {
            ResponseEntity<String> badStatus = put(id, token, "{\"status\":\"BANANA\"}");
            assertThat(badStatus.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            String tooLongColor = "x".repeat(51);
            ResponseEntity<String> badColor = put(id, token, "{\"color\":\"" + tooLongColor + "\"}");
            assertThat(badColor.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            ResponseEntity<String> detail = restTemplate.getForEntity("/api/posts/" + id, String.class);
            assertThat(detail.getBody()).contains("\"status\":\"OPEN\"");
        } finally {
            deletePost(id, token);
        }
    }

    private String randomSpecies() {
        return "sp" + UUID.randomUUID().toString().substring(0, 8);
    }

    private int totalElements(String url) throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody()).get("totalElements").asInt();
    }

    private ResponseEntity<String> put(long id, String token, String body) {
        return restTemplate.exchange(
                "/api/posts/" + id, HttpMethod.PUT, new HttpEntity<>(body, authHeaders(token)), String.class);
    }

    private long createPost(String token, String species) throws Exception {
        String body = """
                {"type":"LOST","species":"%s","color":"orange","size":"medium","hasCollar":true,
                 "description":"original description","eventTime":"2026-10-04T10:00:00+01:00",
                 "latitude":53.3498,"longitude":-6.2603,"publicArea":"Dublin 1"}
                """.formatted(species);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/posts", HttpMethod.POST, new HttpEntity<>(body, authHeaders(token)), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody()).get("id").asLong();
    }

    private void deletePost(long id, String token) {
        restTemplate.exchange(
                "/api/posts/" + id, HttpMethod.DELETE, new HttpEntity<>(authHeaders(token)), String.class);
    }

    private String registerAndGetToken() throws Exception {
        String body = """
                {"email":"%s","password":"password123","displayName":"Update Test User"}
                """.formatted("update-" + UUID.randomUUID() + "@example.com");
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