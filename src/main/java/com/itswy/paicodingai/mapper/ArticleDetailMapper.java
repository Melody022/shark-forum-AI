package com.itswy.paicodingai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itswy.paicodingai.entity.forum.ArticleDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文章详情 Mapper 接口
 */
@Mapper
public interface ArticleDetailMapper extends BaseMapper<ArticleDetail> {

    /**
     * 根据文章ID查询最新版本的文章内容
     *
     * @param articleId 文章ID
     * @return 文章详情
     */
    @Select("SELECT * FROM article_detail WHERE article_id = #{articleId} AND deleted = 0 " +
            "ORDER BY version DESC LIMIT 1")
    ArticleDetail selectLatestByArticleId(@Param("articleId") Long articleId);
}
