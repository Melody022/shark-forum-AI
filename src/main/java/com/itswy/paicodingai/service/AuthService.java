package com.itswy.paicodingai.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itswy.paicodingai.entity.SysUser;
import com.itswy.paicodingai.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 最小登录服务:sys_user + Redis token 会话。
 */
@Slf4j
@Service
public class AuthService {

    public static final String TOKEN_PREFIX = "auth:token:";
    public static final Duration TOKEN_TTL = Duration.ofDays(1);

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthService(SysUserMapper userMapper, PasswordEncoder passwordEncoder, StringRedisTemplate redisTemplate) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
    }

    /** 登录,成功返回 token。 */
    public AuthResult login(String username, String password) {
        SysUser user = findEnabledUser(username);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        Principal p = toPrincipal(user);
        try {
            redisTemplate.opsForValue().set(TOKEN_PREFIX + token, objectMapper.writeValueAsString(p), TOKEN_TTL);
        } catch (Exception e) {
            throw new IllegalStateException("登录状态写入失败", e);
        }
        log.info("用户登录成功: username={}, role={}", user.getUsername(), user.getRoleCode());
        return new AuthResult(token, p);
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            redisTemplate.delete(TOKEN_PREFIX + token);
        }
    }

    /** 校验 token,返回当前登录用户;无效返回 null。 */
    public Principal resolve(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String json = redisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Principal.class);
        } catch (Exception e) {
            log.warn("解析登录态失败", e);
            return null;
        }
    }

    /** 供管理员/初始化创建用户(密码哈希)。 */
    public SysUser createUser(String username, String rawPassword, String nickname, String roleCode) {
        SysUser user = SysUser.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .nickname(nickname)
                .roleCode(roleCode == null ? SysUser.ROLE_USER : roleCode)
                .enabled(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userMapper.insert(user);
        return user;
    }

    private SysUser findEnabledUser(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        try {
            return userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getUsername, username)
                    .eq(SysUser::getEnabled, 1));
        } catch (Exception e) {
            log.warn("查询用户失败(表未就绪?): {}", e.getMessage());
            return null;
        }
    }

    private Principal toPrincipal(SysUser user) {
        return new Principal(String.valueOf(user.getId()), user.getUsername(),
                user.getRoleCode(), user.getNickname());
    }

    public record AuthResult(String token, Principal user) {
    }

    /** 登录用户视图(存 Redis)。 */
    public record Principal(String userId, String username, String roleCode, String nickname) {
    }
}
