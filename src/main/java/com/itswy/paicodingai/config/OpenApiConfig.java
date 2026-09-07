package com.itswy.paicodingai.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger 元信息。
 *
 * <p>浏览器: /swagger-ui.html 或 /swagger-ui/index.html;
 * Apifox 导入: GET /v3/api-docs。</p>
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "paicoding-ai API",
                version = "1.0",
                description = "技术派 AI 客服助手。登录获取 token 后,请求头加 Authorization: Bearer <token>(聊天/会话/后台接口需要登录,/admin 接口需 ADMIN)。"
        ),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "token",
        description = "POST /api/auth/login 登录后返回的 token"
)
public class OpenApiConfig {
}
