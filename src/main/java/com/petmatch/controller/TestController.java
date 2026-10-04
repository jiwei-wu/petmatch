
package com.petmatch.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/api/me")
    public String whoAmI(Authentication authentication) {
        return "当前登录用户：" + authentication.getName();
    }
}