package com.itswy.paicodingai.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itswy.paicodingai.knowledge.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库分块Mapper
 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {
}
