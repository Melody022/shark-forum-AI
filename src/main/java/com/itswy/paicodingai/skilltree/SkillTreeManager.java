package com.itswy.paicodingai.skilltree;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Skill Tree管理器
 */
@Slf4j
@Component
public class SkillTreeManager {

    private final Map<String, SkillNode> nodes = new HashMap<>();
    private final List<String> rootNodes = new ArrayList<>();

    @PostConstruct
    public void init() throws IOException {
        loadSkillTree();
        log.info("Skill Tree初始化完成: {} 个节点", nodes.size());
    }

    /**
     * 加载Skill Tree
     */
    private void loadSkillTree() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource resource = resolver.getResource("classpath:skill-tree/skill-tree.yaml");

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        SkillTreeConfig config = mapper.readValue(resource.getInputStream(), SkillTreeConfig.class);

        for (SkillNodeConfig nodeConfig : config.getTree()) {
            SkillNode node = convertToNode(nodeConfig);
            nodes.put(nodeConfig.getId(), node);

            if (node.getLevel() == 1) {
                rootNodes.add(nodeConfig.getId());
            }
        }
    }

    /**
     * 获取所有节点
     */
    public Collection<SkillNode> getAllNodes() {
        return nodes.values();
    }

    /**
     * 根据ID获取节点
     */
    public SkillNode getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    /**
     * 获取根节点
     */
    public List<SkillNode> getRootNodes() {
        return rootNodes.stream()
            .map(nodes::get)
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * 获取子节点
     */
    public List<SkillNode> getChildren(String nodeId) {
        SkillNode node = nodes.get(nodeId);
        if (node == null || node.getChildren() == null) {
            return List.of();
        }
        return node.getChildren().stream()
            .map(nodes::get)
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * 根据skillFile查找节点
     */
    public List<SkillNode> getNodesBySkillFile(String skillFile) {
        return nodes.values().stream()
            .filter(node -> skillFile.equals(node.getSkillFile()))
            .toList();
    }

    private SkillNode convertToNode(SkillNodeConfig config) {
        SkillNode node = new SkillNode();
        node.setId(config.getId());
        node.setName(config.getName());
        node.setDescription(config.getDescription());
        node.setParentId(config.getParentId());
        node.setLevel(config.getLevel());
        node.setChildren(config.getChildren());
        node.setSkillFile(config.getSkillFile());
        return node;
    }

    @Data
    private static class SkillTreeConfig {
        private List<SkillNodeConfig> tree;
    }

    @Data
    private static class SkillNodeConfig {
        private String id;
        private String name;
        private String description;
        private String parentId;
        private int level;
        private List<String> children;
        private String skillFile;
    }
}
