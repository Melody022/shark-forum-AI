package com.itswy.paicodingai;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.Environment;

import java.net.InetAddress;

@Slf4j
@SpringBootApplication
@MapperScan("com.itswy.paicodingai.mapper")
public class PaicodingAiApplication {

    public static void main(String[] args) throws Exception {
        //创建Spring应用实例
        SpringApplication app = new SpringApplicationBuilder(PaicodingAiApplication.class).build(args);
        //启动Spring应用,启动后获取Environment对象，env中有配置文件
        Environment env = app.run(args).getEnvironment();

        String protocol = "http";
        if(env.getProperty("server.ssl.key-store") != null){
            protocol = "https";
        }
        log.info("\n----------------------------------------------------------\n\t" +
                        "Application '{}' is running! Access URLs:\n\t" +
                        "Local: \t\t{}://localhost:{}\n\t" +
                        "External: \t{}://{}:{}\n\t" +
                        "Profile(s): \t{}\n----------------------------------------------------------",
                env.getProperty("spring.application.name"),
                protocol,
                env.getProperty("server.port"),
                protocol,
                InetAddress.getLocalHost().getHostAddress(),// 获取本机IP
                env.getProperty("server.port"),
                env.getActiveProfiles());
    }

}
