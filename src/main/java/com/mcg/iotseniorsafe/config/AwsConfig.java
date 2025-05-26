package com.mcg.iotseniorsafe.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

// 애플리케이션 전역에서 관리되는 빈 객체 (Root Context)
@Configuration
public class AwsConfig {

    @Bean
    public BedrockRuntimeClient bedrockClient () {
        return BedrockRuntimeClient.builder()
                .region(Region.of("ap-northeast-2"))              // 서울 리전
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();


    }


}
