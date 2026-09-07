package com.itswy.paicodingai.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.itswy.paicodingai.entity.SysUser;
import com.itswy.paicodingai.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;

/**
 * 种子账号:admin/admin123(ADMIN)、user/user123(USER)。
 */
@Slf4j
@Component
@DependsOn("dbSchemaInitializer")
public class UserSeeder {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserSeeder(SysUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void init() {
        insertIfAbsent("admin", "admin123", "管理员", SysUser.ROLE_ADMIN);
        insertIfAbsent("user", "user123", "普通用户", SysUser.ROLE_USER);
        log.info("演示账号就绪: admin/admin123(ADMIN), user/user123(USER)");
    }

    private void insertIfAbsent(String username, String rawPassword, String nickname, String role) {
        try {
            Long count = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
            if (count != null && count > 0) {
                return;
            }
            SysUser user = SysUser.builder()
                    .username(username)
                    .passwordHash(passwordEncoder.encode(rawPassword))
                    .nickname(nickname)
                    .roleCode(role)
                    .enabled(1)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userMapper.insert(user);
        } catch (Exception e) {
            log.warn("写入演示账号失败: username={} - {}", username, e.getMessage());
        }
    }
}
