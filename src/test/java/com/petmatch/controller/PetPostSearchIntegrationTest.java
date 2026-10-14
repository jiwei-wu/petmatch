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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestRestTemplate
class PetPostSearchIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void listFiltersByTypeAndSpecies_caseInsensitive_andCapsPageSize() throws Exception {
        String token = registerAndGetToken();
        String species = "sp" + UUID.randomUUID().toString().substring(0, 8);
        String otherSpecies = "ot" + UUID.randomUUID().toString().substring(0, 8);

        long lostId = createPost(token, "LOST", species);
        long sightingId = createPost(token, "SIGHTING", species);
        long otherId = createPost(token, "LOST", otherSpecies);

        try {
            assertThat(totalElements("/api/posts?species=" + species)).isEqualTo(2);
            assertThat(totalElements("/api/posts?species=" + species.toUpperCase())).isEqualTo(2);
            assertThat(totalElements("/api/posts?species=" + species + "&type=LOST")).isEqualTo(1);
            assertThat(totalElements("/api/posts?species=" + species + "&type=sighting")).isEqualTo(1);
            assertThat(totalElements("/api/posts?type=LOST&species=" + otherSpecies)).isEqualTo(1);
            assertThat(totalElements("/api/posts?species=does-not-exist-" + species)).isEqualTo(0);

            ResponseEntity<String> badType = restTemplate.getForEntity(
                    "/api/posts?type=BANANA", String.class);
            assertThat(badType.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            ResponseEntity<String> hugePage = restTemplate.getForEntity(
                    "/api/posts?size=1000", String.class);
            assertThat(hugePage.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(hugePage.getBody()).contains("\"size\":50");
        } finally {
            deletePost(lostId, token);
            deletePost(sightingId, token);
            deletePost(otherId, token);
        }
    }

    private int totalElements(String url) throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody()).get("totalElements").asInt();
    }

    private long createPost(String token, String type, String species) throws Exception {
        String body = """
                {"type":"%s","species":"%s","eventTime":"2026-10-04T10:00:00+01:00",
                 "latitude":53.3498,"longitude":-6.2603,"publicArea":"Dublin 1"}
                """.formatted(type, species);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/posts", HttpMethod.POST, new HttpEntity<>(body, authHeaders(token)), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody()).get("id").asLong();
    }

    private void deletePost(long postId, String token) {
        restTemplate.exchange(
                "/api/posts/" + postId, HttpMethod.DELETE, new HttpEntity<>(authHeaders(token)), String.class);
    }

    private String registerAndGetToken() throws Exception {
        String body = """
                {"email":"%s","password":"password123","displayName":"Search Test User"}
                """.formatted("search-" + UUID.randomUUID() + "@example.com");
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