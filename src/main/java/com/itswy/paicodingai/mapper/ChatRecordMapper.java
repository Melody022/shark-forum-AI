package com.itswy.paicodingai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itswy.paicodingai.entity.ChatRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天记录 Mapper —— 操作 chat_record 表
 *
 * 继承 BaseMapper 后可以直接使用 CRUD 方法
 * 自动支持 MyBatis-Plus 的 lambdaQuery 和 lambdaUpdate
 */
@Mapper
public interface ChatRecordMapper extends BaseMapper<ChatRecord> {
}
