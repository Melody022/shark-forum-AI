package com.itswy.paicodingai.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 系统提示词配置 —— Nacos 热更新，失败降级读本地文件
 *
 * 启动流程：
 *   1. 尝试连接 Nacos，加载所有提示词到缓存
 *   2. 注册 Listener，配置变更时自动更新缓存
 *   3. Nacos 连接失败时，降级读 classpath/prompts/ 下的文件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemPromptConfig {

    private final NacosProperties nacosProperties;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private ConfigService configService;

    private static final String[] DATA_IDS = {
            "base.md",
            "agent-route",
            "agent-general.md",
            "agent-article.md",
            "agent-konwledge.md"
    };

    /** agentType -> Nacos dataId 映射 */
    private static final Map<String, String> AGENT_DATA_ID_MAP = Map.of(
            "route", "agent-route",
            "general", "agent-general.md",
            "article", "agent-article.md",
            "knowledge", "agent-konwledge.md"
    );

    /** Nacos dataId -> 本地 classpath 文件路径映射（降级用） */
    private static final Map<String, String> LOCAL_PATH_MAP = Map.of(
            "base.md", "prompts/base.md",
            "agent-route", "prompts/agents/route.md",
            "agent-general.md", "prompts/agents/general.md",
            "agent-article.md", "prompts/agents/article.md",
            "agent-konwledge.md", "prompts/agents/knowledge.md"
    );

    @PostConstruct
    public void init() {
        try {
            Properties props = new Properties();
            props.put("serverAddr", nacosProperties.getServerAddr());
            // public 命名空间不能显式设置 namespace，否则 Nacos 2.x 会当作自定义命名空间查询
            String ns = nacosProperties.getNamespace();
            if (ns != null && !ns.isBlank()) {
                props.put("namespace", ns);
            }
            log.info("Nacos 连接参数 => serverAddr: {}, namespace: [{}], group: {}",
                    nacosProperties.getServerAddr(), ns, nacosProperties.getGroup());
            configService = NacosFactory.createConfigService(props);

            for (String dataId : DATA_IDS) {
                loadFromNacos(dataId);
            }
            log.info("Nacos 提示词加载完成，缓存 {} 条", cache.size());
        } catch (Exception e) {
            log.warn("Nacos 连接失败({})，降级读本地文件", e.getMessage());
            loadFromClasspath();
        }
    }

    @PreDestroy
    public void destroy() {
        try { if (configService != null) configService.shutDown(); } catch (Exception ignored) {}
    }

    public String getSystemMessage(String agentType) {
        String base = cache.getOrDefault("base.md", "");
        String dataId = AGENT_DATA_ID_MAP.getOrDefault(agentType.toLowerCase(), "");
        String agent = cache.getOrDefault(dataId, "");
        return base + "\n" + agent;
    }

    private void loadFromNacos(String dataId) {
        try {
            String group = nacosProperties.getGroup();
            String content = configService.getConfig(dataId, group, 5000);
            if (content != null && !content.isEmpty()) {
                cache.put(dataId, content);
                log.info("Nacos 加载成功：{}", dataId);
            } else {
                log.warn("Nacos 返回空：{}，降级读本地", dataId);
                loadFromClasspathSingle(dataId);
            }

            configService.addListener(dataId, group, new Listener() {
                @Override
                public Executor getExecutor() { return Executors.newSingleThreadExecutor(); }
                @Override
                public void receiveConfigInfo(String info) {
                    if (info != null && !info.isEmpty()) {
                        cache.put(dataId, info);
                        log.info("提示词热更新：{}", dataId);
                    }
                }
            });
        } catch (Exception e) {
            log.warn("Nacos 加载失败：{}，降级读本地", dataId);
            loadFromClasspathSingle(dataId);
        }
    }

    private void loadFromClasspath() {
        for (String dataId : DATA_IDS) {
            loadFromClasspathSingle(dataId);
        }
    }

    private void loadFromClasspathSingle(String dataId) {
        try {
            String localPath = LOCAL_PATH_MAP.getOrDefault(dataId, "prompts/" + dataId);
            var resource = new ClassPathResource(localPath);
            if (resource.exists()) {
                cache.put(dataId, StreamUtils.copyToString(
                        resource.getInputStream(), StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            log.warn("本地文件读取失败：{}", dataId);
        }
    }
}
