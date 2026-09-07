package com.itswy.paicodingai.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itswy.paicodingai.knowledge.entity.KnowledgeTableRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识库表格行 Mapper
 */
@Mapper
public interface KnowledgeTableRowMapper extends BaseMapper<KnowledgeTableRow> {

    /**
     * 查询表格的所有行数据
     */
    @Select("SELECT * FROM knowledge_table_row WHERE table_id = #{tableId} ORDER BY row_index, field_name")
    List<KnowledgeTableRow> selectByTableId(@Param("tableId") String tableId);

    /**
     * 查询表格的元数据（表头和字段列表）
     */
    @Select("SELECT DISTINCT field_name FROM knowledge_table_row WHERE table_id = #{tableId}")
    List<String> selectFieldNames(@Param("tableId") String tableId);

    /**
     * 统计表格行数
     */
    @Select("SELECT COUNT(DISTINCT row_index) FROM knowledge_table_row WHERE table_id = #{tableId}")
    int countRows(@Param("tableId") String tableId);
}
