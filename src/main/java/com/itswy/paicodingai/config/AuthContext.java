package com.itswy.paicodingai.config;

/**
 * 当前请求身份上下文(ThreadLocal)。
 *
 * <p>由 AuthInterceptor(M4)写入;未登录/未接入时回退默认游客身份:</p>
 * <ul>
 *   <li>userId = "0"(旧前端硬编码值)</li>
 *   <li>role = "USER"(仅读工具)</li>
 * </ul>
 */
public final class AuthContext {

    private static final ThreadLocal<Principal> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public record Principal(String userId, String roleCode, String nickname) {
    }

    public static void set(String userId, String roleCode, String nickname) {
        HOLDER.set(new Principal(userId, roleCode, nickname));
    }

    public static Principal get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 当前 userId;无则 "0"。 */
    public static String currentUserId() {
        Principal p = HOLDER.get();
        return p != null && p.userId() != null && !p.userId().isBlank() ? p.userId() : "0";
    }

    /** 当前角色;无则 "USER"。 */
    public static String currentRole() {
        Principal p = HOLDER.get();
        return p != null && p.roleCode() != null && !p.roleCode().isBlank() ? p.roleCode() : "USER";
    }

    public static boolean isAuthenticated() {
        return HOLDER.get() != null;
    }
}
