package com.mcg.iotseniorsafe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcg.iotseniorsafe.dto.AlertResponse;
import com.mcg.iotseniorsafe.dto.SensorDataDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BedrockService {
    private static final Logger logger = LoggerFactory.getLogger(BedrockService.class);

    private final BedrockRuntimeClient client;
    private final ObjectMapper objectMapper;

    @Value("${app.bedrock.modelId}")
    private String modelId;

    @Value("${app.bedrock.inferenceProfileArn}")
    private String inferenceProfileArn;

    public BedrockService(BedrockRuntimeClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    public AlertResponse analyzeAndGenerateAlert(List<SensorDataDto> sensorDataList) {
        // 위험 센서 필터링
        List<SensorDataDto> dangerousSensors = sensorDataList.stream()
                .filter(sensor -> sensor.getValue() > sensor.getThreshold())
                .collect(Collectors.toList());

        if (dangerousSensors.isEmpty()) {
            return createSafeResponse();
        }

        // 프롬프트 생성
        String prompt = buildPrompt(sensorDataList, dangerousSensors);

        try {
            // Bedrock 호출
            String response = callBedrock(prompt);

            // 응답 파싱
            return parseBedrockResponse(response);

        } catch (Exception e) {
            logger.error("Bedrock 호출 중 오류 발생", e);
            return createErrorResponse();
        }
    }

    private String buildPrompt(List<SensorDataDto> allSensors, List<SensorDataDto> dangerousSensors) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("독거노인 거주지의 IoT 센서 데이터를 분석하여 위험 상황을 판단하고 적절한 대응 방안을 제시해주세요.\n\n");
        prompt.append("### 전체 센서 데이터:\n");

        for (SensorDataDto sensor : allSensors) {
            prompt.append(String.format("- %s 센서 (%s): 측정값 %.2f (임계값: %.2f) %s\n",
                    sensor.getSensorType(),
                    sensor.getLocation(),
                    sensor.getValue(),
                    sensor.getThreshold(),
                    sensor.getValue() > sensor.getThreshold() ? "[위험]" : "[정상]"));
        }

        prompt.append("\n### 위험 감지된 센서:\n");
        for (SensorDataDto sensor : dangerousSensors) {
            prompt.append(String.format("- %s 센서: %.2f (임계값 %.2f 초과)\n",
                    sensor.getSensorType(), sensor.getValue(), sensor.getThreshold()));
        }

        prompt.append("\n다음 형식으로 JSON 응답을 생성해주세요:\n");
        prompt.append("{\n");
        prompt.append("  \"riskLevel\": \"위험도 수준 (낮음/보통/높음/매우높음)\",\n");
        prompt.append("  \"situation\": \"현재 상황 설명\",\n");
        prompt.append("  \"recommendation\": \"대응 지침 (줄바꿈은 \\n으로 표시)\",\n");
        prompt.append("  \"reportingAgency\": \"신고가 필요한 경우 기관명 (예: 119, 112, 지역복지센터 등)\",\n");
        prompt.append("  \"contactNumber\": \"해당 기관 연락처\"\n");
        prompt.append("}\n");

        return prompt.toString();
    }

    private String callBedrock(String prompt) {
        Message userMessage = Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(prompt))
                .build();

        ConverseRequest request = ConverseRequest.builder()
                .modelId(inferenceProfileArn != null ? inferenceProfileArn : modelId)
                .messages(userMessage)
                .build();

        ConverseResponse response = client.converse(request);
        return response.output().message().content().get(0).text();
    }

    private AlertResponse parseBedrockResponse(String responseText) {
        try {
            // JSON 부분만 추출 (Bedrock이 추가 텍스트를 포함할 수 있음)
            int startIndex = responseText.indexOf("{");
            int endIndex = responseText.lastIndexOf("}") + 1;

            if (startIndex != -1 && endIndex > startIndex) {
                String jsonPart = responseText.substring(startIndex, endIndex);
                return objectMapper.readValue(jsonPart, AlertResponse.class);
            }

            // JSON을 찾지 못한 경우 기본 응답 생성
            return createDefaultResponse(responseText);

        } catch (Exception e) {
            logger.error("Bedrock 응답 파싱 중 오류", e);
            return createDefaultResponse(responseText);
        }
    }

    private AlertResponse createDefaultResponse(String text) {
        AlertResponse response = new AlertResponse();
        response.setRiskLevel("높음");
        response.setSituation("센서 데이터 분석 결과 이상 징후가 감지되었습니다.");
        response.setRecommendation(text);
        response.setReportingAgency("119");
        response.setContactNumber("119");
        return response;
    }

    private AlertResponse createSafeResponse() {
        AlertResponse response = new AlertResponse();
        response.setRiskLevel("낮음");
        response.setSituation("모든 센서 값이 정상 범위 내에 있습니다.");
        response.setRecommendation("현재 특별한 조치가 필요하지 않습니다.");
        return response;
    }

    private AlertResponse createErrorResponse() {
        AlertResponse response = new AlertResponse();
        response.setRiskLevel("확인필요");
        response.setSituation("AI 분석 중 오류가 발생했습니다.");
        response.setRecommendation("시스템 관리자에게 문의하세요.");
        return response;
    }
}