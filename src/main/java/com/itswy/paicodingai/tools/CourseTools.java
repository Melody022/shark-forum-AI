package com.itswy.paicodingai.tools;

import com.itswy.paicodingai.service.forum.ForumColumnService;
import com.itswy.paicodingai.tools.result.CourseInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 教程工具 - Tool Calling实现
 *
 * 使用 forum 数据库的专栏功能模拟教程功能
 * 参考天机学堂实现：Tool执行结果存入ToolResultHolder，SSE流最后返回给前端
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseTools {

    private final ForumColumnService forumColumnService;

    /**
     * 根据ID查询教程详情
     *
     * @param courseId 教程ID
     * @param toolContext 工具上下文（包含requestId）
     * @return 教程详情
     */
    @Tool(description = "根据教程ID查询教程详情，返回教程名称、描述、作者等信息")
    public CourseInfo queryCourseById(
            @ToolParam(description = "教程ID") Long courseId,
            ToolContext toolContext) {
        log.info("查询教程详情: courseId={}", courseId);
        try {
            CourseInfo courseInfo = forumColumnService.getColumnById(courseId);
            if (courseInfo == null) {
                throw new RuntimeException("教程不存在: courseId=" + courseId);
            }

            // ★ 关键：将查询结果存入ToolResultHolder，供SSE流最后返回给前端
            String requestId = (String) toolContext.getContext().get("requestId");
            if (requestId != null) {
                String fieldKey = "courseInfo_" + courseId;
                ToolResultHolder.put(requestId, fieldKey, courseInfo);
                log.info("教程查询结果已存入ToolResultHolder: requestId={}, field={}", requestId, fieldKey);
            }

            return courseInfo;
        } catch (Exception e) {
            log.error("查询教程失败: courseId={}", courseId, e);
            throw new RuntimeException("教程查询失败：" + e.getMessage(), e);
        }
    }

    /**
     * 查询教程列表
     *
     * @param limit 返回数量限制
     * @param toolContext 工具上下文
     * @return 教程列表
     */
    @Tool(description = "查询教程列表，返回推荐的教程专栏")
    public List<CourseInfo> queryCourseList(
            @ToolParam(description = "返回数量限制，默认5") Integer limit,
            ToolContext toolContext) {
        log.info("查询教程列表: limit={}", limit);
        try {
            if (limit == null || limit <= 0) {
                limit = 5;
            }
            List<CourseInfo> courses = forumColumnService.getColumnList(limit);

            // 存入ToolResultHolder
            String requestId = (String) toolContext.getContext().get("requestId");
            if (requestId != null && !courses.isEmpty()) {
                ToolResultHolder.put(requestId, "courseList", courses);
                log.info("教程列表已存入ToolResultHolder: requestId={}, count={}", requestId, courses.size());
            }

            return courses;
        } catch (Exception e) {
            log.error("查询教程列表失败", e);
            throw new RuntimeException("教程列表查询失败：" + e.getMessage(), e);
        }
    }

    /**
     * 根据关键词搜索教程
     *
     * @param keyword 搜索关键词
     * @param limit 返回数量限制
     * @param toolContext 工具上下文
     * @return 教程列表
     */
    @Tool(description = "根据关键词搜索教程，返回匹配的教程列表")
    public List<CourseInfo> searchCourses(
            @ToolParam(description = "搜索关键词") String keyword,
            @ToolParam(description = "返回数量限制，默认5") Integer limit,
            ToolContext toolContext) {
        log.info("搜索教程: keyword={}, limit={}", keyword, limit);
        try {
            if (limit == null || limit <= 0) {
                limit = 5;
            }
            List<CourseInfo> courses = forumColumnService.searchColumns(keyword, limit);

            // 存入ToolResultHolder
            String requestId = (String) toolContext.getContext().get("requestId");
            if (requestId != null && !courses.isEmpty()) {
                ToolResultHolder.put(requestId, "searchResult_courses", courses);
                log.info("教程搜索结果已存入ToolResultHolder: requestId={}, count={}", requestId, courses.size());
            }

            return courses;
        } catch (Exception e) {
            log.error("搜索教程失败: keyword={}", keyword, e);
            throw new RuntimeException("教程搜索失败：" + e.getMessage(), e);
        }
    }

    /**
     * 查询推荐教程
     *
     * @param level 难度级别：初级/中级/高级
     * @param limit 返回数量限制
     * @param toolContext 工具上下文
     * @return 推荐教程列表
     */
    @Tool(description = "查询推荐教程，返回最新的教程专栏")
    public List<CourseInfo> queryRecommendedCourses(
            @ToolParam(description = "难度级别：初级/中级/高级（暂未实现分级）") String level,
            @ToolParam(description = "返回数量限制，默认5") Integer limit,
            ToolContext toolContext) {
        log.info("查询推荐教程: level={}, limit={}", level, limit);
        try {
            if (limit == null || limit <= 0) {
                limit = 5;
            }
            // 简化实现：返回最新专栏，暂不支持难度分级
            List<CourseInfo> courses = forumColumnService.getColumnList(limit);

            // 存入ToolResultHolder
            String requestId = (String) toolContext.getContext().get("requestId");
            if (requestId != null && !courses.isEmpty()) {
                ToolResultHolder.put(requestId, "recommendedCourses", courses);
                log.info("推荐教程已存入ToolResultHolder: requestId={}, count={}", requestId, courses.size());
            }

            return courses;
        } catch (Exception e) {
            log.error("查询推荐教程失败", e);
            throw new RuntimeException("推荐教程查询失败：" + e.getMessage(), e);
        }
    }
}
