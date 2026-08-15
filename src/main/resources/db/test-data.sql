-- 插入更多测试数据到paicoding数据库

-- 1. 插入更多分类（如果不存在）
INSERT IGNORE INTO category (id, category_name, status, create_time, update_time) VALUES
(10, 'Spring Boot', 1, NOW(), NOW()),
(11, '微服务', 1, NOW(), NOW()),
(12, '数据库', 1, NOW(), NOW()),
(13, '前端开发', 1, NOW(), NOW()),
(14, 'DevOps', 1, NOW(), NOW()),
(15, '系统设计', 1, NOW(), NOW()),
(16, '性能优化', 1, NOW(), NOW()),
(17, '安全', 1, NOW(), NOW()),
(18, '架构设计', 1, NOW(), NOW());

-- 2. 插入更多文章（50篇）
INSERT INTO article (user_id, article_type, title, short_title, url_slug, picture, summary, category_id, source, status, create_time, update_time) VALUES
-- Java基础
(1, 1, 'Java 21新特性详解：虚拟线程、模式匹配、记录类', 'Java 21新特性', '/article/java-21-features', 'https://picsum.photos/seed/java21/800/400', '深入了解Java 21的核心新特性，包括虚拟线程、模式匹配、记录类等，提升开发效率', 1, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(1, 1, '深入理解JVM内存模型：堆、栈、方法区', 'JVM内存模型', '/article/jvm-memory-model', 'https://picsum.photos/seed/jvm/800/400', '全面解析JVM内存结构，理解垃圾回收机制，优化Java应用性能', 1, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(1, 1, 'Java并发编程实战：线程池、锁、并发集合', 'Java并发编程', '/article/java-concurrency', 'https://picsum.photos/seed/concurrent/800/400', '深入讲解Java并发编程核心知识，掌握多线程开发技巧', 1, 1, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),

-- Spring Boot
(1, 1, 'Spring Boot 3.0实战：自动配置、Starter、Actuator', 'Spring Boot 3.0', '/article/spring-boot-3', 'https://picsum.photos/seed/springboot3/800/400', 'Spring Boot 3.0新特性详解，快速上手企业级开发', 10, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(1, 1, 'Spring Boot集成MyBatis-Plus：CRUD、分页、代码生成', 'MyBatis-Plus集成', '/article/spring-boot-mybatis-plus', 'https://picsum.photos/seed/mybatis/800/400', '详解Spring Boot与MyBatis-Plus集成，提升开发效率', 10, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(1, 1, 'Spring Boot优雅处理全局异常：@ControllerAdvice实战', '全局异常处理', '/article/spring-boot-exception', 'https://picsum.photos/seed/exception/800/400', '实现优雅的全局异常处理，提升系统健壮性', 10, 1, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),

-- Spring Cloud
(1, 1, 'Spring Cloud微服务架构：Nacos、Feign、Gateway', 'Spring Cloud微服务', '/article/spring-cloud-microservice', 'https://picsum.photos/seed/microservice/800/400', 'Spring Cloud微服务架构实战，服务注册、调用、网关', 11, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(1, 1, 'Nacos配置中心实战：动态配置、多环境管理', 'Nacos配置中心', '/article/nacos-config', 'https://picsum.photos/seed/nacos/800/400', '详解Nacos配置中心使用，实现配置热更新', 11, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),

-- 数据库
(1, 1, 'MySQL索引优化实战：B+树、联合索引、覆盖索引', 'MySQL索引优化', '/article/mysql-index-optimization', 'https://picsum.photos/seed/mysql/800/400', '深入理解MySQL索引原理，优化查询性能', 12, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(1, 1, 'Redis缓存策略：缓存穿透、击穿、雪崩解决方案', 'Redis缓存策略', '/article/redis-cache-strategy', 'https://picsum.photos/seed/redis/800/400', '详解Redis缓存常见问题及解决方案，保障系统稳定性', 12, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(1, 1, 'Elasticsearch实战：倒排索引、分词器、聚合查询', 'ES实战', '/article/elasticsearch-practice', 'https://picsum.photos/seed/es/800/400', 'Elasticsearch核心概念与实战，构建高性能搜索系统', 12, 1, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),

-- 前端开发
(1, 1, 'Vue 3组合式API实战：ref、reactive、computed', 'Vue 3组合式API', '/article/vue3-composition-api', 'https://picsum.photos/seed/vue3/800/400', 'Vue 3组合式API详解，提升前端开发效率', 13, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(1, 1, 'React Hooks深入：useState、useEffect、useContext', 'React Hooks', '/article/react-hooks', 'https://picsum.photos/seed/react/800/400', 'React Hooks核心原理与实战，简化组件逻辑', 13, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(1, 1, 'TypeScript高级类型：泛型、条件类型、映射类型', 'TypeScript高级类型', '/article/typescript-advanced-types', 'https://picsum.photos/seed/ts/800/400', 'TypeScript高级类型系统，提升代码类型安全', 13, 1, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),

-- DevOps
(1, 1, 'Docker容器化部署：Dockerfile、Compose、镜像优化', 'Docker容器化', '/article/docker-containerization', 'https://picsum.photos/seed/docker/800/400', 'Docker容器化最佳实践，简化应用部署', 14, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(1, 1, 'Kubernetes入门：Pod、Service、Deployment', 'K8s入门', '/article/kubernetes-introduction', 'https://picsum.photos/seed/k8s/800/400', 'Kubernetes核心概念入门，容器编排基础', 14, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(1, 1, 'CI/CD流水线：Jenkins、GitHub Actions、GitLab CI', 'CI/CD流水线', '/article/ci-cd-pipeline', 'https://picsum.photos/seed/cicd/800/400', '构建自动化CI/CD流水线，提升发布效率', 14, 1, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),

-- 系统设计
(1, 1, '分布式系统设计：CAP理论、一致性、分区容错', '分布式系统设计', '/article/distributed-system-design', 'https://picsum.photos/seed/distributed/800/400', '分布式系统核心理论，设计高可用架构', 15, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(1, 1, '微服务拆分策略：领域驱动设计(DDD)实践', 'DDD实践', '/article/ddd-practice', 'https://picsum.photos/seed/ddd/800/400', '领域驱动设计在微服务中的应用', 15, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),

-- 性能优化
(1, 1, 'Java性能调优：JVM参数、GC优化、线程调优', 'Java性能调优', '/article/java-performance-tuning', 'https://picsum.photos/seed/performance/800/400', 'Java应用性能调优实战，提升系统吞吐量', 16, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(1, 1, 'SQL性能优化：慢查询分析、执行计划、索引优化', 'SQL性能优化', '/article/sql-performance-optimization', 'https://picsum.photos/seed/sql/800/400', 'SQL查询性能优化技巧，提升数据库响应速度', 16, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),

-- 安全
(1, 1, 'Spring Security实战：JWT认证、OAuth2、RBAC权限', 'Spring Security', '/article/spring-security', 'https://picsum.photos/seed/security/800/400', 'Spring Security安全框架实战，实现完整认证授权', 17, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(1, 1, 'Web安全防护：XSS、CSRF、SQL注入、文件上传漏洞', 'Web安全防护', '/article/web-security', 'https://picsum.photos/seed/websec/800/400', '常见Web安全漏洞及防护措施，保障系统安全', 17, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),

-- 架构设计
(1, 1, '高并发架构设计：限流、降级、熔断、缓存', '高并发架构', '/article/high-concurrency-architecture', 'https://picsum.photos/seed/concurrency/800/400', '高并发系统架构设计，保障系统稳定性', 18, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(1, 1, '消息队列实战：RabbitMQ、Kafka、RocketMQ对比', '消息队列', '/article/message-queue', 'https://picsum.photos/seed/mq/800/400', '主流消息队列对比与实战，解耦系统架构', 18, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),

-- 项目实战
(1, 1, '技术派全方位视角解读：社区系统架构设计', '技术派架构', '/article/paicoding-architecture', 'https://picsum.photos/seed/paicoding/800/400', '技术派社区系统完整架构解析，前后端分离最佳实践', 9, 1, 1, NOW(), NOW()),
(1, 1, '开源项目推荐：10个值得学习的Java项目', 'Java开源项目', '/article/java-open-source-projects', 'https://picsum.photos/seed/opensource/800/400', '精选10个高质量Java开源项目，提升编程能力', 9, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),

-- 更多文章
(1, 1, 'Spring AI实战：接入大模型、RAG、Tool Calling', 'Spring AI实战', '/article/spring-ai-practice', 'https://picsum.photos/seed/springai/800/400', 'Spring AI框架实战，快速接入AI能力', 10, 1, 1, NOW(), NOW()),
(1, 1, 'RAG技术详解：检索增强生成、向量数据库、Embedding', 'RAG技术', '/article/rag-technology', 'https://picsum.photos/seed/rag/800/400', 'RAG技术原理与实战，构建智能问答系统', 10, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(1, 1, '多Agent架构设计：路由、技能、工作流', '多Agent架构', '/article/multi-agent-architecture', 'https://picsum.photos/seed/agent/800/400', 'AI Agent架构设计，实现智能任务分发', 10, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(1, 1, 'Java 17 LTS新特性：Sealed Classes、Pattern Matching', 'Java 17新特性', '/article/java-17-features', 'https://picsum.photos/seed/java17/800/400', 'Java 17长期支持版本新特性详解', 1, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(1, 1, 'Stream API高级用法：并行流、收集器、自定义操作', 'Stream高级用法', '/article/stream-api-advanced', 'https://picsum.photos/seed/stream/800/400', 'Java Stream API高级技巧，简化集合操作', 1, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(1, 1, '设计模式实战：单例、工厂、策略、观察者模式', '设计模式', '/article/design-patterns', 'https://picsum.photos/seed/pattern/800/400', '常用设计模式实战，提升代码质量', 1, 1, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
(1, 1, 'Spring Boot集成Swagger：API文档自动生成', 'Swagger集成', '/article/spring-boot-swagger', 'https://picsum.photos/seed/swagger/800/400', 'Spring Boot集成Swagger，自动生成API文档', 10, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(1, 1, 'Docker Compose多容器编排：MySQL、Redis、Nginx', 'Docker Compose', '/article/docker-compose', 'https://picsum.photos/seed/compose/800/400', 'Docker Compose实战，一键部署多容器应用', 14, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(1, 1, 'Git工作流：GitFlow、Trunk-Based、GitHub Flow', 'Git工作流', '/article/git-workflow', 'https://picsum.photos/seed/git/800/400', 'Git分支管理最佳实践，提升团队协作效率', 14, 1, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
(1, 1, 'MySQL主从复制：原理、配置、读写分离', 'MySQL主从复制', '/article/mysql-replication', 'https://picsum.photos/seed/replication/800/400', 'MySQL主从复制实战，实现高可用数据库架构', 12, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(1, 1, 'Redis集群搭建：哨兵模式、Cluster模式', 'Redis集群', '/article/redis-cluster', 'https://picsum.photos/seed/rediscluster/800/400', 'Redis高可用集群搭建，保障缓存服务稳定', 12, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(1, 1, 'Vue 3 + TypeScript + Vite项目搭建', 'Vue3+TS+Vite', '/article/vue3-ts-vite', 'https://picsum.photos/seed/vite/800/400', 'Vue 3 + TypeScript + Vite现代前端项目搭建', 13, 1, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
(1, 1, 'React 18新特性：Suspense、Concurrent Mode', 'React 18新特性', '/article/react-18-features', 'https://picsum.photos/seed/react18/800/400', 'React 18并发特性详解，提升用户体验', 13, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(1, 1, 'TypeScript装饰器：类装饰器、方法装饰器、属性装饰器', 'TS装饰器', '/article/typescript-decorators', 'https://picsum.photos/seed/decorators/800/400', 'TypeScript装饰器原理与实战', 13, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(1, 1, '分布式事务解决方案：Seata、TCC、Saga', '分布式事务', '/article/distributed-transaction', 'https://picsum.photos/seed/transaction/800/400', '分布式事务主流解决方案对比与实战', 15, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(1, 1, '服务治理：服务发现、负载均衡、熔断降级', '服务治理', '/article/service-governance', 'https://picsum.photos/seed/governance/800/400', '微服务治理核心概念与实践', 15, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(1, 1, 'JVM调优工具：JVisualVM、Arthas、JProfiler', 'JVM调优工具', '/article/jvm-tuning-tools', 'https://picsum.photos/seed/jvmtools/800/400', 'JVM性能调优工具实战，定位性能瓶颈', 16, 1, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
(1, 1, '缓存设计模式：Cache-Aside、Read-Through、Write-Through', '缓存设计模式', '/article/cache-design-patterns', 'https://picsum.photos/seed/cachepattern/800/400', '缓存设计模式详解，提升系统性能', 16, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(1, 1, 'OAuth2.0授权流程：授权码、客户端凭证、密码模式', 'OAuth2.0', '/article/oauth2-flow', 'https://picsum.photos/seed/oauth/800/400', 'OAuth2.0授权流程详解，实现安全认证', 17, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(1, 1, 'HTTPS原理：SSL/TLS握手、证书、加密算法', 'HTTPS原理', '/article/https-principle', 'https://picsum.photos/seed/https/800/400', 'HTTPS安全通信原理详解', 17, 1, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
(1, 1, 'API网关设计：Kong、Spring Cloud Gateway、Nginx', 'API网关', '/article/api-gateway', 'https://picsum.photos/seed/gateway/800/400', 'API网关架构设计与实现', 18, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(1, 1, '事件驱动架构：Event Sourcing、CQRS模式', '事件驱动架构', '/article/event-driven-architecture', 'https://picsum.photos/seed/event/800/400', '事件驱动架构设计，实现系统解耦', 18, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY));

-- 3. 插入文章详情（对应每篇文章）
INSERT INTO article_detail (article_id, content, edit_type, version, create_time, update_time)
SELECT
    a.id,
    CONCAT('# ', a.title, '\n\n', a.summary, '\n\n## 文章内容\n\n这里是详细的文章内容...'),
    1,
    1,
    a.create_time,
    a.update_time
FROM article a
WHERE a.id NOT IN (SELECT article_id FROM article_detail);

-- 4. 插入文章标签关联
INSERT INTO article_tag (article_id, tag_id, create_time)
SELECT
    a.id,
    t.id,
    NOW()
FROM article a
CROSS JOIN tag t
WHERE t.tag_name IN ('Java', 'Spring', 'Spring Boot', 'MySQL', 'Redis', 'Vue', 'React', 'Docker', 'Kubernetes', '微服务', '架构', '性能优化')
AND a.id NOT IN (SELECT article_id FROM article_tag)
LIMIT 100;

-- 5. 插入更多教程专栏
INSERT INTO column_info (user_id, column_name, introduction, cover, status, create_time, update_time) VALUES
(1, 'Java核心技能', 'Java语言核心技能体系，从入门到精通', 'https://picsum.photos/seed/javacore/800/400', 1, NOW(), NOW()),
(1, 'Spring全家桶', 'Spring、Spring Boot、Spring Cloud完整学习路径', 'https://picsum.photos/seed/springfamily/800/400', 1, NOW(), NOW()),
(1, '数据库深度优化', 'MySQL、Redis、Elasticsearch深度优化实践', 'https://picsum.photos/seed/dboptimization/800/400', 1, NOW(), NOW()),
(1, '前端现代化开发', 'Vue 3、React、TypeScript现代前端技术栈', 'https://picsum.photos/seed/frontend/800/400', 1, NOW(), NOW()),
(1, '云原生与DevOps', 'Docker、Kubernetes、CI/CD云原生技术', 'https://picsum.photos/seed/cloudnative/800/400', 1, NOW(), NOW()),
(1, 'AI应用开发实战', 'Spring AI、RAG、Agent AI应用开发', 'https://picsum.photos/seed/aidev/800/400', 1, NOW(), NOW());

-- 验证插入结果
SELECT '插入完成' as status;
SELECT COUNT(*) as total_articles FROM article;
SELECT COUNT(*) as total_details FROM article_detail;
SELECT COUNT(*) as total_columns FROM column_info;
