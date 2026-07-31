package com.itswy.paicodingai.constants;

/**
 * ==========================================================================
 * 常量定义
 * ==========================================================================
 *
 * 把所有"魔法值"（硬编码的字符串/数字）集中在这里定义，
 * 避免代码里到处是散落的字符串，改的时候好找、不容易出错。
 *
 * 比如后面 RouteAgent 要传一个叫 requestId 的参数给 Tool，
 * 这个 "requestId" 字符串如果直接写在代码里，
 * 哪天拼写错了很难排查。集中定义在这里，IDE能自动补全。
 *
 * @date 2026-07-18
 */
public interface Constant {

    // ======== 通用字段名 ========

    /** 请求ID，用于关联前端的某一次请求 */
    String REQUEST_ID = "requestId";

    /** 用户ID */
    String USER_ID = "userId";

    /** 大模型返回的结束原因标记 */
    String STOP = "STOP";

    // ======== Redis 中用的 Key ========

    /** 生成状态的 Redis Hash Key */
    String GENERATE_STATUS_KEY = "GENERATE_STATUS";

    // ======== 工具描述（大模型通过描述决定是否调用工具） ========

    interface Tools {
        /** 文章查询工具的描述 */
        String QUERY_ARTICLE_BY_ID = "根据文章id查询文章的详细信息，包括标题、内容、分类等";
    }

    // ======== 工具参数描述 ========

    interface ToolParams {
        /** 文章ID参数的描述 */
        String ARTICLE_ID = "文章的id编号";
    }
}
