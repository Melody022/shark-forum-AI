package com.itswy.paicodingai.service.forum;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itswy.paicodingai.entity.forum.ColumnInfo;
import com.itswy.paicodingai.mapper.ColumnInfoMapper;
import com.itswy.paicodingai.tools.result.CourseInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Forum 专栏服务
 *
 * 使用专栏功能模拟教程功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForumColumnService {

    private final ColumnInfoMapper columnInfoMapper;

    /**
     * 根据ID查询专栏详情
     *
     * @param columnId 专栏ID
     * @return 专栏详情
     */
    public CourseInfo getColumnById(Long columnId) {
        log.info("从数据库查询专栏详情: columnId={}", columnId);

        ColumnInfo column = columnInfoMapper.selectById(columnId);
        if (column == null) {
            log.warn("专栏不存在: columnId={}", columnId);
            return null;
        }

        // 转换为 CourseInfo
        return CourseInfo.builder()
                .id(column.getId())
                .name(column.getColumnName())
                .description(column.getIntroduction())
                .coverUrl(column.getCover())
                .authorId(column.getUserId())
                .authorName("")  // 需要关联 user_info 表查询
                .price(0.0)  // 专栏暂时免费
                .publishTime(column.getPublishTime() != null ? column.getPublishTime().toString() : null)
                .build();
    }

    /**
     * 查询专栏列表
     *
     * @param limit 返回数量限制
     * @return 专栏列表
     */
    public List<CourseInfo> getColumnList(int limit) {
        log.info("从数据库查询专栏列表: limit={}", limit);

        LambdaQueryWrapper<ColumnInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ColumnInfo::getState, 1, 2)  // 已连载或已完结
               .orderByDesc(ColumnInfo::getCreateTime)
               .last("LIMIT " + limit);

        List<ColumnInfo> columns = columnInfoMapper.selectList(wrapper);

        return columns.stream()
                .map(column -> CourseInfo.builder()
                        .id(column.getId())
                        .name(column.getColumnName())
                        .description(column.getIntroduction())
                        .coverUrl(column.getCover())
                        .authorId(column.getUserId())
                        .publishTime(column.getPublishTime() != null ? column.getPublishTime().toString() : null)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 根据关键词搜索专栏
     *
     * @param keyword 搜索关键词
     * @param limit 返回数量限制
     * @return 专栏列表
     */
    public List<CourseInfo> searchColumns(String keyword, int limit) {
        log.info("从数据库搜索专栏: keyword={}, limit={}", keyword, limit);

        LambdaQueryWrapper<ColumnInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ColumnInfo::getState, 1, 2)
               .and(w -> w.like(ColumnInfo::getColumnName, keyword)
                         .or()
                         .like(ColumnInfo::getIntroduction, keyword))
               .orderByDesc(ColumnInfo::getCreateTime)
               .last("LIMIT " + limit);

        List<ColumnInfo> columns = columnInfoMapper.selectList(wrapper);

        return columns.stream()
                .map(column -> CourseInfo.builder()
                        .id(column.getId())
                        .name(column.getColumnName())
                        .description(column.getIntroduction())
                        .coverUrl(column.getCover())
                        .authorId(column.getUserId())
                        .publishTime(column.getPublishTime() != null ? column.getPublishTime().toString() : null)
                        .build())
                .collect(Collectors.toList());
    }
}
