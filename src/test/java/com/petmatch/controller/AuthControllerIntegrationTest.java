package com.petmatch.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
class AuthControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String randomEmail() {
        return "test-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void register_thenLogin_thenAccessProtectedEndpoint_shouldSucceed() throws Exception {
        String email = randomEmail();

        // 1. 注册
        String registerBody = """
                {"email":"%s","password":"password123","displayName":"Integration Test User"}
                """.formatted(email);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        ResponseEntity<String> registerResponse = restTemplate.postForEntity(
                "/api/auth/register",
                new HttpEntity<>(registerBody, headers),
                String.class
        );

        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode registerJson = objectMapper.readTree(registerResponse.getBody());
        assertThat(registerJson.get("email").asText()).isEqualTo(email);
        assertThat(registerJson.get("token").asText()).isNotBlank();

        // 2. 用同一邮箱重复注册,应该被拒绝
        ResponseEntity<String> duplicateResponse = restTemplate.postForEntity(
                "/api/auth/register",
                new HttpEntity<>(registerBody, headers),
                String.class
        );
        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 3. 登录
        String loginBody = """
                {"email":"%s","password":"password123"}
                """.formatted(email);

        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(loginBody, headers),
                String.class
        );

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode loginJson = objectMapper.readTree(loginResponse.getBody());
        String token = loginJson.get("token").asText();
        assertThat(token).isNotBlank();

        // 4. 密码错误应该被拒绝
        String wrongPasswordBody = """
                {"email":"%s","password":"wrongpassword"}
                """.formatted(email);

        ResponseEntity<String> wrongLoginResponse = restTemplate.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(wrongPasswordBody, headers),
                String.class
        );
        assertThat(wrongLoginResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 5. 不带 token 访问受保护接口,应该被拒绝
        ResponseEntity<String> noAuthResponse = restTemplate.getForEntity("/api/me", String.class);
        assertThat(noAuthResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 6. 带着登录拿到的 token 访问受保护接口,应该成功并识别出正确的用户
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.set("Authorization", "Bearer " + token);

        ResponseEntity<String> authResponse = restTemplate.exchange(
                "/api/me",
                org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                String.class
        );

        assertThat(authResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(authResponse.getBody()).contains(email);
    }
}