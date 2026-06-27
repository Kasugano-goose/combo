package com.example.combo.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AppConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * WebSocket 通知发送专用线程池（Spring 托管，优雅停机时自动等待任务完成）。
     *
     * <p>核心线程数 4，最大 16。<b>队列容量设为 0（SynchronousQueue）</b>——
     * 这样任务无法排队，会立即触发线程扩展到 maxPoolSize=16。
     * 若 16 个线程全忙，第 17 个任务由调用线程同步执行（CallerRunsPolicy），
     * 形成自然背压，避免通知无限积压。
     *
     * <p>之前配置 queueCapacity=200 时，ThreadPoolExecutor 会先把任务排队，
     * 队列满了才扩线程，导致 maxPoolSize=16 实际上永远不会生效，
     * 所有超出 corePoolSize=4 的任务都要在队列里等——而 sendWithRetry 内
     * 有 Thread.sleep（最长约 65s），会造成严重延迟。
     */
    @Bean(name = "notifyExecutor")
    public Executor notifyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(0); // SynchronousQueue：不排队，直接扩线程
        executor.setThreadNamePrefix("ws-notify-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOriginPatterns("http://localhost:*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}
