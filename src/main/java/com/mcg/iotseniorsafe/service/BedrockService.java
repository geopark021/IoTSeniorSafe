package com.mcg.iotseniorsafe.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

import java.util.ArrayList;
import java.util.List;

@Service
public class BedrockService {
    private final BedrockRuntimeClient client;

    @Value("${app.bedrock.modelId}")
    private String modelId; // aws bedrock 사용 모델

    @Value("${app.bedrock.inferenceProfileArn}")
    private String inferenceProfileArn;

    public BedrockService(BedrockRuntimeClient client) {
        this.client = client;
    }


    // led, 재실감지, 소리 센서 기반 알림 메세지 작성
    // 기준 미정 : 논의 진행 예정
    // 가정 : 각 센서 별로 threshold 존재
    public List<String> generateAlerts(double[] sensorValues, double[] thresholds) {
        List<String> alerts = new ArrayList<>();
        for (int i = 0; i < sensorValues.length; i++) {
            // 기준치 이상인 경우
            if (sensorValues[i] > thresholds[i]) {
                String prompt = String.format(
                        "센서%d 값이 %.2f로 임계치 %.2f를 초과했습니다.",
                        i+1, sensorValues[i], thresholds[i]
                );
                // Bedrock 호출
                String text = client.converse(
                        ConverseRequest.builder()
                                .modelId(inferenceProfileArn)
                                .messages(
                                        Message.builder()
                                                .role(ConversationRole.USER)
                                                .content(ContentBlock.fromText(prompt))
                                                .build()
                                )
                                .build()
                ).output().message().content().get(0).text();
                alerts.add(text);
            }
        }
        return alerts;
    }
}