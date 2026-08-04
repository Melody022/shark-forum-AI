package com.itswy.paicodingai;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.core.env.Environment;

import java.net.InetAddress;

/**
 * paicoding-ai 启动类
 *
 * 启用Feign：支持通过HTTP调用其他微服务（paicoding）
 * 配合Nacos实现服务发现和负载均衡
 */
@Slf4j
@SpringBootApplication
@MapperScan("com.itswy.paicodingai.mapper")
@EnableFeignClients  // ★ 启用Feign客户端，用于调用paicoding微服务
public class PaicodingAiApplication {

    public static void main(String[] args) throws Exception {
        SpringApplication app = new SpringApplicationBuilder(PaicodingAiApplication.class).build(args);
        Environment env = app.run(args).getEnvironment();

        String protocol = "http";
        if (env.getProperty("server.ssl.key-store") != null) {
            protocol = "https";
        }

        String host = InetAddress.getLocalHost().getHostAddress();
        String port = env.getProperty("server.port", "8081");

        log.info("\n"
                + "=========================================================\n"
                + "  paicoding-ai 启动成功！\n"
                + "---------------------------------------------------------\n"
                + "  📌 控制台页面（启动即用）：\n"
                + "     {}://localhost:{}/ai-chat.html\n"
                + "\n"
                + "  📌 API 文档：\n"
                + "     {}://localhost:{}/ai/session        POST  创建会话\n"
                + "     {}://localhost:{}/ai/chat           POST  流式对话\n"
                + "     {}://localhost:{}/ai/stop           POST  停止生成\n"
                + "     {}://localhost:{}/ai/history        GET   历史会话\n"
                + "=========================================================\n",
                protocol, port,
                protocol, port,
                protocol, port,
                protocol, port,
                protocol, port,
                protocol, port
        );
    }
}
