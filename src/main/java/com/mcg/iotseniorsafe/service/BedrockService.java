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
import java.util.Map;
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

    // 위험 판단 기준값
    private static final int CRITICAL_ZERO_COUNT_24H = 24; // 24시간 연속 0값
    private static final int WARNING_ZERO_COUNT_12H = 12;  // 12시간 연속 0값
    private static final int WARNING_ZERO_COUNT_8H = 8;    // 8시간 연속 0값

    public BedrockService(BedrockRuntimeClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    public AlertResponse analyzeAndGenerateAlert(List<SensorDataDto> sensorDataList) {
        // 센서 타입별로 그룹화 (기존 방식과 동일)
        Map<String, List<SensorDataDto>> sensorGroups = sensorDataList.stream()
                .collect(Collectors.groupingBy(SensorDataDto::getSensorType));

        // 재실 및 소음 센서 데이터 분석
        List<SensorDataDto> occupancySensors = sensorGroups.getOrDefault("OCCUPANCY", List.of());
        List<SensorDataDto> noiseSensors = sensorGroups.getOrDefault("NOISE", List.of());

        // 기존 위험 센서 필터링 로직 유지 (하위 호환성)
        List<SensorDataDto> dangerousSensors = sensorDataList.stream()
                .filter(sensor -> sensor.getValue() > sensor.getThreshold())
                .collect(Collectors.toList());

        // 재실/소음 센서가 없는 경우 기존 방식으로 처리
        if (occupancySensors.isEmpty() && noiseSensors.isEmpty()) {
            if (dangerousSensors.isEmpty()) {
                return createSafeResponse();
            }
            // 기존 프롬프트 방식 사용
            String prompt = buildPrompt(sensorDataList, dangerousSensors);
            try {
                String response = callBedrock(prompt);
                return parseBedrockResponse(response);
            } catch (Exception e) {
                logger.error("Bedrock 호출 중 오류 발생", e);
                return createErrorResponse();
            }
        }

        // 새로운 방식: 재실/소음 센서 기반 분석
        RiskAnalysis riskAnalysis = analyzeRiskLevel(occupancySensors, noiseSensors);

        if (riskAnalysis.riskLevel.equals("낮음") && dangerousSensors.isEmpty()) {
            return createSafeResponse();
        }

        // 개선된 프롬프트 생성
        String prompt = buildAdvancedPrompt(sensorDataList, occupancySensors, noiseSensors, riskAnalysis);

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

    private RiskAnalysis analyzeRiskLevel(List<SensorDataDto> occupancySensors, List<SensorDataDto> noiseSensors) {
        RiskAnalysis analysis = new RiskAnalysis();

        // 재실 센서 분석
        if (!occupancySensors.isEmpty()) {
            long occupancyZeroCount = occupancySensors.stream()
                    .mapToLong(sensor -> sensor.getValue() == 0 ? 1 : 0)
                    .sum();
            analysis.occupancyZeroCount = (int) occupancyZeroCount;
            analysis.occupancyTotalCount = occupancySensors.size();
        }

        // 소음 센서 분석
        if (!noiseSensors.isEmpty()) {
            long noiseZeroCount = noiseSensors.stream()
                    .mapToLong(sensor -> sensor.getValue() == 0 ? 1 : 0)
                    .sum();
            analysis.noiseZeroCount = (int) noiseZeroCount;
            analysis.noiseTotalCount = noiseSensors.size();
        }

        // 위험도 판단
        analysis.riskLevel = determineRiskLevel(analysis);

        return analysis;
    }

    private String determineRiskLevel(RiskAnalysis analysis) {
        // 24시간 연속 무반응 (매우 위험)
        if (analysis.occupancyZeroCount >= CRITICAL_ZERO_COUNT_24H ||
                analysis.noiseZeroCount >= CRITICAL_ZERO_COUNT_24H) {
            return "매우높음";
        }

        // 12시간 연속 무반응 (높은 위험)
        if (analysis.occupancyZeroCount >= WARNING_ZERO_COUNT_12H ||
                analysis.noiseZeroCount >= WARNING_ZERO_COUNT_12H) {
            return "높음";
        }

        // 8시간 연속 무반응 (보통 위험)
        if (analysis.occupancyZeroCount >= WARNING_ZERO_COUNT_8H ||
                analysis.noiseZeroCount >= WARNING_ZERO_COUNT_8H) {
            return "보통";
        }

        return "낮음";
    }

    private String buildAdvancedPrompt(List<SensorDataDto> allSensors,
                                       List<SensorDataDto> occupancySensors,
                                       List<SensorDataDto> noiseSensors,
                                       RiskAnalysis riskAnalysis) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("독거노인 거주지의 IoT 센서 데이터를 분석하여 위험 상황을 판단하고 적절한 대응 방안을 제시해주세요.\n\n");

        prompt.append("### 분석 기준:\n");
        prompt.append("- 움직임 감지 센서와 소음 감지 센서 데이터를 1시간 단위로 수집\n");
        prompt.append("- 센서 값이 0인 경우: 해당 시간에 움직임 또는 소음이 감지되지 않음\n");
        prompt.append("- 위험 판단 기준:\n");
        prompt.append("  * 24시간 연속 0값: 매우 위험 (고독사 위험)\n");
        prompt.append("  * 12시간 연속 0값: 높은 위험\n");
        prompt.append("  * 8시간 연속 0값: 보통 위험\n\n");

        // 재실 센서 데이터
        if (occupancySensors != null && !occupancySensors.isEmpty()) {
            prompt.append("### 움직임 감지 센서 데이터 (최근 24시간):\n");
            for (int i = 0; i < occupancySensors.size(); i++) {
                SensorDataDto sensor = occupancySensors.get(i);
                prompt.append(String.format("- %d시간 전: %.0f (%s에서 측정) %s\n",
                        i + 1,
                        sensor.getValue(),
                        sensor.getLocation(),
                        sensor.getValue() == 0 ? "[무반응]" : "[감지됨]"));
            }
            prompt.append(String.format("움직임 무반응 시간: %d시간 / %d시간\n\n",
                    riskAnalysis.occupancyZeroCount, riskAnalysis.occupancyTotalCount));
        }

        // 소음 센서 데이터
        if (noiseSensors != null && !noiseSensors.isEmpty()) {
            prompt.append("### 소음 감지 센서 데이터 (최근 24시간):\n");
            for (int i = 0; i < noiseSensors.size(); i++) {
                SensorDataDto sensor = noiseSensors.get(i);
                prompt.append(String.format("- %d시간 전: %.0f (%s에서 측정) %s\n",
                        i + 1,
                        sensor.getValue(),
                        sensor.getLocation(),
                        sensor.getValue() == 0 ? "[무음]" : "[소음감지]"));
            }
            prompt.append(String.format("소음 무반응 시간: %d시간 / %d시간\n\n",
                    riskAnalysis.noiseZeroCount, riskAnalysis.noiseTotalCount));
        }

        prompt.append("### 현재 위험도 평가: ").append(riskAnalysis.riskLevel).append("\n\n");

        // 기타 센서 데이터 (참고용)
        List<SensorDataDto> otherSensors = allSensors.stream()
                .filter(sensor -> !"OCCUPANCY".equals(sensor.getSensorType()) &&
                        !"NOISE".equals(sensor.getSensorType()))
                .collect(Collectors.toList());

        if (!otherSensors.isEmpty()) {
            prompt.append("### 기타 센서 데이터 (참고):\n");
            for (SensorDataDto sensor : otherSensors) {
                prompt.append(String.format("- %s 센서 (%s): 측정값 %.2f\n",
                        sensor.getSensorType(),
                        sensor.getLocation(),
                        sensor.getValue()));
            }
            prompt.append("\n");
        }

        prompt.append("위의 데이터를 종합적으로 분석하여 다음 형식으로 JSON 응답을 생성해주세요:\n");
        prompt.append("{\n");
        prompt.append("  \"riskLevel\": \"위험도 수준 (낮음/보통/높음/매우높음)\",\n");
        prompt.append("  \"situation\": \"현재 상황에 대한 상세한 분석 (연속 무반응 시간, 패턴 분석 포함)\",\n");
        prompt.append("  \"recommendation\": \"구체적인 대응 지침 (즉시 조치사항, 확인 방법 등, 줄바꿈은 \\n으로 표시)\",\n");
        prompt.append("  \"reportingAgency\": \"신고가 필요한 경우 기관명 (119: 응급상황, 112: 안전확인, 지역복지센터: 일반 안부확인)\",\n");
        prompt.append("  \"contactNumber\": \"해당 기관 연락처\",\n");
        prompt.append("  \"urgencyLevel\": \"긴급도 (즉시/신속/보통/경과관찰)\"\n");
        prompt.append("}\n\n");

        prompt.append("※ 24시간 연속 무반응의 경우 고독사 가능성을 고려하여 즉시 119 신고를 권장해주세요.");

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
        response.setSituation("움직임 및 소음 센서에서 정상적인 활동이 감지되고 있습니다.");
        response.setRecommendation("현재 특별한 조치가 필요하지 않습니다. 지속적인 모니터링을 계속합니다.");
        return response;
    }

    private AlertResponse createErrorResponse() {
        AlertResponse response = new AlertResponse();
        response.setRiskLevel("확인필요");
        response.setSituation("AI 분석 중 오류가 발생했습니다.");
        response.setRecommendation("시스템 관리자에게 문의하세요.");
        return response;
    }

    private AlertResponse createInsufficientDataResponse() {
        AlertResponse response = new AlertResponse();
        response.setRiskLevel("확인필요");
        response.setSituation("움직임 또는 소음 센서 데이터가 충분하지 않습니다.");
        response.setRecommendation("센서 연결 상태를 확인하고 데이터 수집을 재시도해주세요.");
        return response;
    }

    // 기존 프롬프트 메서드 유지 (하위 호환성)
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

    // 위험도 분석을 위한 내부 클래스
    private static class RiskAnalysis {
        String riskLevel = "낮음";
        int occupancyZeroCount = 0;
        int occupancyTotalCount = 0;
        int noiseZeroCount = 0;
        int noiseTotalCount = 0;
    }
}