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
class PetPostPrivacyIntegrationTest {

    private static final String POST_BODY = """
            {"type":"LOST","species":"cat","color":"orange","size":"medium",
             "hasCollar":true,"description":"privacy test cat",
             "eventTime":"2026-10-04T10:00:00+01:00",
             "latitude":53.3498,"longitude":-6.2603,"publicArea":"Dublin 1"}
            """;

    @Autowired
    private TestRestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publicResponses_neverExposeExactLocationOrInternalFields() throws Exception {
        String token = registerAndGetToken();

        ResponseEntity<String> created = createPost(token);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(created.getBody()).contains("\"publicArea\":\"Dublin 1\"");
        assertNoSensitiveData(created.getBody());

        long postId = objectMapper.readTree(created.getBody()).get("id").asLong();

        ResponseEntity<String> detail = restTemplate.getForEntity("/api/posts/" + postId, String.class);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertNoSensitiveData(detail.getBody());

        ResponseEntity<String> list = restTemplate.getForEntity("/api/posts", String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertNoSensitiveData(list.getBody());

        deletePost(postId, token);
    }

    @Test
    void onlyTheOwnerCanDeleteAPost() throws Exception {
        String ownerToken = registerAndGetToken();
        String otherToken = registerAndGetToken();

        long postId = objectMapper.readTree(createPost(ownerToken).getBody()).get("id").asLong();

        ResponseEntity<String> otherDelete = deletePost(postId, otherToken);
        assertThat(otherDelete.getStatusCode().is4xxClientError()).isTrue();
        assertThat(restTemplate.getForEntity("/api/posts/" + postId, String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> ownerDelete = deletePost(postId, ownerToken);
        assertThat(ownerDelete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(restTemplate.getForEntity("/api/posts/" + postId, String.class).getStatusCode()
                .is4xxClientError()).isTrue();
    }

    @Test
    void createPost_withoutToken_isRejected() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/posts", HttpMethod.POST, new HttpEntity<>(POST_BODY, jsonHeaders()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createPost_withInvalidEventTime_returnsBadRequest() throws Exception {
        String token = registerAndGetToken();
        String badBody = POST_BODY.replace("2026-10-04T10:00:00+01:00", "not-a-date");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/posts", HttpMethod.POST, new HttpEntity<>(badBody, authHeaders(token)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private String registerAndGetToken() throws Exception {
        String body = """
                {"email":"%s","password":"password123","displayName":"Privacy Test User"}
                """.formatted("privacy-" + UUID.randomUUID() + "@example.com");
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/register", new HttpEntity<>(body, jsonHeaders()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody()).get("token").asText();
    }

    private ResponseEntity<String> createPost(String token) {
        return restTemplate.exchange(
                "/api/posts", HttpMethod.POST, new HttpEntity<>(POST_BODY, authHeaders(token)), String.class);
    }

    private ResponseEntity<String> deletePost(long postId, String token) {
        return restTemplate.exchange(
                "/api/posts/" + postId, HttpMethod.DELETE, new HttpEntity<>(authHeaders(token)), String.class);
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

    private void assertNoSensitiveData(String json) {
        assertThat(json).doesNotContain(
                "\"latitude\"", "\"longitude\"", "\"exactLocation\"", "\"microchipHash\"", "\"userId\"");
        assertThat(json).doesNotContain("53.3498", "-6.2603");
    }
}