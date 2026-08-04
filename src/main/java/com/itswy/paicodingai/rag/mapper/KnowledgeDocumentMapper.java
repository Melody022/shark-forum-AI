package com.itswy.paicodingai.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itswy.paicodingai.rag.model.KnowledgeDocumentEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库文档Mapper
 */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocumentEntity> {
}
