package com.example.combo.common.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Configuration
public class WebSocketConfig {

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new MockServletContextSafeServerEndpointExporter();
    }

    private static class MockServletContextSafeServerEndpointExporter extends ServerEndpointExporter {

        @Override
        public void afterPropertiesSet() {
            if (getServerContainer() != null) {
                super.afterPropertiesSet();
            }
        }

        @Override
        public void afterSingletonsInstantiated() {
            if (getServerContainer() != null) {
                super.afterSingletonsInstantiated();
            }
        }
    }
}
