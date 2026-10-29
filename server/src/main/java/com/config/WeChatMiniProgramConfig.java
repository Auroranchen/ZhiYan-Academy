package com.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WeChatMiniProgramProperties.class)
public class WeChatMiniProgramConfig {
}
