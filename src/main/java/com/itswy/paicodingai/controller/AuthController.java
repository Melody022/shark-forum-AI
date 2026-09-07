package com.itswy.paicodingai.controller;

import com.itswy.paicodingai.config.AuthContext;
import com.itswy.paicodingai.config.AuthInterceptor;
import com.itswy.paicodingai.dto.AuthLoginRequest;
import com.itswy.paicodingai.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 最小登录接口。登录成功同时下发 HttpOnly cookie,供页面导航鉴权(见 AuthInterceptor)。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 登录。body: {"username":"admin","password":"admin123"} */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody AuthLoginRequest req, HttpServletResponse response) {
        Map<String, Object> result = new HashMap<>();
        try {
            String username = req.getUsername();
            String password = req.getPassword();
            AuthService.AuthResult auth = authService.login(username, password);
            result.put("code", 200);
            result.put("data", Map.of("token", auth.token(), "user", auth.user()));
            setCookie(response, auth.token(), 86400);
        } catch (Exception e) {
            result.put("code", 401);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletResponse response) {
        String token = bearer(authorization);
        if (token != null) {
            authService.logout(token);
        }
        setCookie(response, "", 0);
        return Map.of("code", 200, "data", "ok");
    }

    /** 当前登录用户。 */
    @GetMapping("/me")
    public Map<String, Object> me() {
        AuthContext.Principal p = AuthContext.get();
        if (p == null) {
            return Map.of("code", 401, "message", "未登录");
        }
        return Map.of("code", 200, "data",
                Map.of("userId", p.userId(), "roleCode", p.roleCode(), "nickname", p.nickname()));
    }

    private void setCookie(HttpServletResponse response, String value, int maxAgeSeconds) {
        response.addHeader("Set-Cookie", AuthInterceptor.TOKEN_COOKIE + "=" + value
                + "; Path=/; Max-Age=" + maxAgeSeconds + "; HttpOnly; SameSite=Lax");
    }

    private String bearer(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        return authorization;
    }
}
