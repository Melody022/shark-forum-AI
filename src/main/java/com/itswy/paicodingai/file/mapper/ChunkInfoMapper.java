package com.itswy.paicodingai.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itswy.paicodingai.file.entity.ChunkInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文件分片Mapper
 */
@Mapper
public interface ChunkInfoMapper extends BaseMapper<ChunkInfo> {

    /**
     * 查询文件的所有分片
     */
    @Select("SELECT * FROM chunk_info WHERE file_md5 = #{fileMd5} ORDER BY chunk_index")
    List<ChunkInfo> findByFileMd5(@Param("fileMd5") String fileMd5);

    /**
     * 查询文件已上传的分片数量
     */
    @Select("SELECT COUNT(*) FROM chunk_info WHERE file_md5 = #{fileMd5}")
    int countByFileMd5(@Param("fileMd5") String fileMd5);
}
