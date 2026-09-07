package com.itswy.paicodingai.config;

import com.itswy.paicodingai.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 登录拦截。
 *
 * <p>两种身份来源:Authorization: Bearer(前端 fetch)或 cookie paicoding_ai_token(页面导航)。
 * <ul>
 *   <li>页面(/ai-chat.html、/admin、/admin/console):未登录 → 302 到门户 "/";admin 页需 ADMIN。</li>
 *   <li>接口(/chat、/session、/api/** 等):未登录 → 401 JSON;/admin/api 需 ADMIN。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String TOKEN_COOKIE = "paicoding_ai_token";

    private final AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        if (isOpen(path)) {
            return true;
        }

        String token = bearer(request.getHeader("Authorization"));
        if (token == null || token.isBlank()) {
            token = cookieToken(request);
        }
        AuthService.Principal p = authService.resolve(token);

        // 页面访问:未登录/admin 页非 ADMIN → 重定向到门户
        if (isUserPage(path) || isAdminPage(path)) {
            if (p == null) {
                response.sendRedirect("/");
                return false;
            }
            if (isAdminPage(path) && !"ADMIN".equals(p.roleCode())) {
                response.sendRedirect("/");
                return false;
            }
            AuthContext.set(p.userId(), p.roleCode(), p.nickname());
            return true;
        }

        // 接口访问
        if (p == null) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录或登录已过期");
            return false;
        }
        if (path.startsWith("/admin/api") && !"ADMIN".equals(p.roleCode())) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN, "需要管理员权限");
            return false;
        }
        AuthContext.set(p.userId(), p.roleCode(), p.nickname());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthContext.clear();
    }

    private boolean isUserPage(String path) {
        return "/ai-chat.html".equals(path);
    }

    private boolean isAdminPage(String path) {
        return "/admin".equals(path) || "/admin/console".equals(path);
    }

    private boolean isOpen(String path) {
        if (path == null) {
            return false;
        }
        return path.equals("/")                  // 门户页(登录)
                || path.equals("/api/auth/login")
                || path.equals("/api/auth/logout")
                || path.startsWith("/v3/api-docs")    // OpenAPI 文档
                || path.startsWith("/swagger-ui")     // Swagger UI
                || path.startsWith("/article")
                || path.startsWith("/course")
                || path.startsWith("/knowledge")
                || path.startsWith("/api/mcp/test")
                || path.equals("/api/skills")
                || path.equals("/api/mcp/tools")
                || path.equals("/api/system/info")
                || path.startsWith("/chat/intent");
    }

    private String bearer(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        return authorization;
    }

    private String cookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (TOKEN_COOKIE.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }

    private void writeJson(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\"}");
    }
}
