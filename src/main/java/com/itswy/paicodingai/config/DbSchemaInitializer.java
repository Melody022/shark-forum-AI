package com.itswy.paicodingai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

/**
 * 启动时自动执行全部类路径迁移脚本 db/migration/V*.sql(兼容当前无 Flyway 的现状)。
 *
 * <p>脚本需为单语句 + 分号结尾 + "--" 注释;语句按 {@code Set} 中的 MySQL 错误码做幂等容错,
 * 即重复建表(1050)/重复列(1060)/重复索引(1061)视为"已应用"而跳过,其余真实错误以 WARN 提示并继续,
 * 避免既有库/新库因重复执行而中断启动。</p>
 */
@Slf4j
@Component
public class DbSchemaInitializer {

    /** 视为"已应用/重复执行"的 MySQL 错误码:表已存在、重复列、重复索引。 */
    private static final Set<Integer> IGNORABLE_CODES = Set.of(1050, 1060, 1061);

    private final JdbcTemplate jdbcTemplate;

    public DbSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        try {
            Resource[] scripts = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:db/migration/V*.sql");
            Arrays.sort(scripts, Comparator.comparing(Resource::getFilename, Comparator.nullsLast(String::compareTo)));

            for (Resource script : scripts) {
                try {
                    String content = readScript(script);
                    executeScript(content, script.getFilename());
                } catch (Exception e) {
                    log.warn("执行迁移脚本失败: {} - {}", script.getFilename(), e.getMessage());
                }
            }
            log.info("启动迁移执行完成,共 {} 个脚本", scripts.length);
        } catch (IOException e) {
            log.warn("扫描迁移脚本失败: {}", e.getMessage());
        }
    }

    private String readScript(Resource script) throws IOException {
        return new String(script.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void executeScript(String content, String scriptName) {
        StringBuilder stmt = new StringBuilder();
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            stmt.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String sql = stmt.toString().trim();
                stmt.setLength(0);
                if (!sql.isEmpty()) {
                    executeTolerant(sql, scriptName);
                }
            }
        }
    }

    private void executeTolerant(String sql, String scriptName) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            if (isIgnorable(e)) {
                log.debug("迁移语句已应用(跳过): [{}] {}", scriptName, firstLine(sql));
            } else {
                log.warn("迁移语句执行失败(继续): [{}] {} - {}", scriptName, firstLine(sql), rootMessage(e));
            }
        }
    }

    private boolean isIgnorable(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof SQLException se && IGNORABLE_CODES.contains(se.getErrorCode())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private String firstLine(String sql) {
        String one = sql.trim().replaceAll("\\s+", " ");
        return one.length() > 90 ? one.substring(0, 90) + "…" : one;
    }

    private String rootMessage(Exception e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
