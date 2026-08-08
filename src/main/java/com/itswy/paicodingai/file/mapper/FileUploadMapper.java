package com.itswy.paicodingai.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itswy.paicodingai.file.entity.FileUpload;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件上传Mapper
 */
@Mapper
public interface FileUploadMapper extends BaseMapper<FileUpload> {
}
