package com.itswy.paicodingai.skilltree;

import lombok.Data;
import java.util.List;

/**
 * Skill树节点
 */
@Data
public class SkillNode {

    /** 节点ID */
    private String id;

    /** 节点名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 父节点ID（null表示根节点） */
    private String parentId;

    /** 层级（1或2） */
    private int level;

    /** 子节点ID */
    private List<String> children;

    /** 对应的SKILL.md文件名 */
    private String skillFile;
}
