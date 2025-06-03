package com.mcg.iotseniorsafe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcg.iotseniorsafe.dto.AlertResponse;
import com.mcg.iotseniorsafe.dto.HouseholdComparisonDto;
import com.mcg.iotseniorsafe.dto.SensorDataDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class BedrockService {
    private static final Logger logger = LoggerFactory.getLogger(BedrockService.class);

    private final BedrockRuntimeClient client;
    private final ObjectMapper objectMapper;

    @Qualifier("secondaryJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.bedrock.modelId}")
    private String modelId;

    @Value("${app.bedrock.inferenceProfileArn}")
    private String inferenceProfileArn;

    // 위험도 판단 기준값
    private static final double NORMAL_THRESHOLD = 60.0; // 60% 초과 시 정상
    private static final double SUSPICIOUS_THRESHOLD = 60.0; // 60% 이하 시 의심
    private static final double CRITICAL_THRESHOLD = 40.0; // 40% 이하 시 심각

    @Autowired
    public BedrockService(BedrockRuntimeClient client, ObjectMapper objectMapper, @Qualifier("secondaryJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 레거시 호환성을 위한 메서드 (실제로는 사용 안함)
     */
    public AlertResponse analyzeAndGenerateAlert(List<SensorDataDto> sensorDataList) {
        logger.info("레거시 메서드 호출됨 - 새로운 시스템 사용을 권장합니다.");

        AlertResponse response = new AlertResponse();
        response.setRiskLevel("확인필요");
        response.setSituation("레거시 분석 기능입니다. 새로운 AI 리포팅 기능을 사용해주세요.");
        response.setRecommendation("위험 의심 내역에서 가구를 선택하여 분석하세요.");
        response.setReportingAgency("시스템관리자");
        response.setContactNumber("내부문의");

        return response;
    }

    /**
     * 특정 가구의 어제-오늘 데이터 비교 분석 (새로운 메인 기능)
     */
    public AlertResponse analyzeHouseholdRisk(int householdId) {
        try {
            logger.info("가구 위험도 분석 시작: householdId={}", householdId);

            // 어제와 오늘 데이터 조회
            HouseholdComparisonDto comparisonData = getHouseholdComparisonData(householdId);

            if (comparisonData == null || !comparisonData.hasValidData()) {
                logger.warn("가구 데이터가 충분하지 않음: householdId={}", householdId);
                return createInsufficientDataResponse();
            }

            // 공통 데이터 비율 계산
            double commonDataRatio = calculateCommonDataRatio(comparisonData);
            logger.info("공통 데이터 비율 계산 완료: {}%", commonDataRatio);

            // 위험도 레벨 결정
            String riskLevel = determineRiskLevel(commonDataRatio);
            logger.info("위험도 레벨 결정: {}", riskLevel);

            if ("정상".equals(riskLevel)) {
                return createSafeResponse(commonDataRatio);
            }

            // AI 분석을 위한 프롬프트 생성
            String prompt = buildHouseholdAnalysisPrompt(comparisonData, commonDataRatio, riskLevel);

            // Bedrock 호출
            long startTime = System.currentTimeMillis();
            String response = callBedrock(prompt);
            long processingTime = System.currentTimeMillis() - startTime;

            logger.info("Bedrock 응답 완료: {}ms", processingTime);

            // 응답 파싱
            AlertResponse alertResponse = parseBedrockResponse(response);
            alertResponse.setCommonDataRatio(commonDataRatio);
            alertResponse.setHouseholdId(String.valueOf(householdId));

            // AI 분석 로그 기록
            logAIAnalysis(householdId, "household_analysis", prompt, response, processingTime, true, null);

            return alertResponse;

        } catch (Exception e) {
            logger.error("가구 위험도 분석 중 오류 발생: householdId={}", householdId, e);

            // 에러 로그 기록
            logAIAnalysis(householdId, "household_analysis", null, null, 0, false, e.getMessage());

            return createErrorResponse();
        }
    }

    /**
     * 신고용 상세 보고서 생성
     */
    public AlertResponse generateReportingDocument(int householdId, AlertResponse initialAnalysis) {
        try {
            logger.info("신고 문서 생성 시작: householdId={}", householdId);

            HouseholdComparisonDto comparisonData = getHouseholdComparisonData(householdId);

            if (comparisonData == null) {
                logger.warn("신고 문서 생성을 위한 데이터 부족: householdId={}", householdId);
                return createInsufficientDataResponse();
            }

            String reportingPrompt = buildReportingPrompt(comparisonData, initialAnalysis);

            // Bedrock 호출
            long startTime = System.currentTimeMillis();
            String response = callBedrock(reportingPrompt);
            long processingTime = System.currentTimeMillis() - startTime;

            logger.info("신고 문서 Bedrock 응답 완료: {}ms", processingTime);

            AlertResponse reportingDocument = parseBedrockResponse(response);

            // AI 분석 로그 기록
            logAIAnalysis(householdId, "reporting_document", reportingPrompt, response, processingTime, true, null);

            return reportingDocument;

        } catch (Exception e) {
            logger.error("신고 문서 생성 중 오류 발생: householdId={}", householdId, e);

            // 에러 로그 기록
            logAIAnalysis(householdId, "reporting_document", null, null, 0, false, e.getMessage());

            return createErrorResponse();
        }
    }

    /**
     * 어제와 오늘의 가구 데이터 조회
     */
    private HouseholdComparisonDto getHouseholdComparisonData(int householdId) {
        try {
            String tableName = "sensor_summary_" + householdId;

            // 테이블 존재 여부 확인
            String checkTableQuery = "SELECT COUNT(*) FROM information_schema.tables " +
                    "WHERE table_schema = DATABASE() AND table_name = ?";
            Integer tableExists = jdbcTemplate.queryForObject(checkTableQuery, Integer.class, tableName);

            if (tableExists == null || tableExists == 0) {
                logger.warn("센서 요약 테이블이 존재하지 않음: {}", tableName);
                return null;
            }

            // 어제 데이터 조회 (24시간)
            String yesterdayQuery = String.format(
                    "SELECT recorded_at, led_master_room, led_living_room, led_kitchen, led_toilet, " +
                            "is_occupied, is_noisy FROM %s " +
                            "WHERE DATE(recorded_at) = DATE(NOW() - INTERVAL 1 DAY) " +
                            "ORDER BY recorded_at", tableName);

            // 오늘 데이터 조회 (24시간)
            String todayQuery = String.format(
                    "SELECT recorded_at, led_master_room, led_living_room, led_kitchen, led_toilet, " +
                            "is_occupied, is_noisy FROM %s " +
                            "WHERE DATE(recorded_at) = DATE(NOW()) " +
                            "ORDER BY recorded_at", tableName);

            List<Map<String, Object>> yesterdayData = jdbcTemplate.queryForList(yesterdayQuery);
            List<Map<String, Object>> todayData = jdbcTemplate.queryForList(todayQuery);

            logger.debug("데이터 조회 완료 - 어제: {}시간, 오늘: {}시간", yesterdayData.size(), todayData.size());

            return new HouseholdComparisonDto(householdId, yesterdayData, todayData);

        } catch (Exception e) {
            logger.error("가구 데이터 조회 실패: householdId={}", householdId, e);
            return null;
        }
    }

    /**
     * 어제와 오늘 데이터의 공통 활동 비율 계산
     */
    private double calculateCommonDataRatio(HouseholdComparisonDto comparisonData) {
        List<Map<String, Object>> yesterdayData = comparisonData.getYesterdayData();
        List<Map<String, Object>> todayData = comparisonData.getTodayData();

        if (yesterdayData.isEmpty() || todayData.isEmpty()) {
            logger.warn("데이터 부족 - 어제: {}, 오늘: {}", yesterdayData.size(), todayData.size());
            return 0.0;
        }

        int commonActivityHours = 0;
        int totalHours = Math.min(yesterdayData.size(), todayData.size());

        for (int i = 0; i < totalHours; i++) {
            Map<String, Object> yesterdayHour = yesterdayData.get(i);
            Map<String, Object> todayHour = todayData.get(i);

            // 각 시간대별 활동 패턴 비교
            boolean hasCommonActivity = compareHourlyActivity(yesterdayHour, todayHour);
            if (hasCommonActivity) {
                commonActivityHours++;
            }
        }

        double ratio = totalHours > 0 ? (double) commonActivityHours / totalHours * 100 : 0.0;
        logger.debug("공통 활동 계산: {}/{}시간 = {}%", commonActivityHours, totalHours, ratio);

        return ratio;
    }

    /**
     * 시간대별 활동 패턴 비교
     */
    private boolean compareHourlyActivity(Map<String, Object> yesterdayHour, Map<String, Object> todayHour) {
        // LED 센서 활동 비교
        boolean ledActivityYesterday = isAnyLedActive(yesterdayHour);
        boolean ledActivityToday = isAnyLedActive(todayHour);

        // 재실 감지 센서 비교
        boolean occupancyYesterday = getBooleanValue(yesterdayHour, "is_occupied");
        boolean occupancyToday = getBooleanValue(todayHour, "is_occupied");

        // 소음 감지 센서 비교
        boolean noiseYesterday = getBooleanValue(yesterdayHour, "is_noisy");
        boolean noiseToday = getBooleanValue(todayHour, "is_noisy");

        // 하나라도 공통 활동이 있으면 true
        return (ledActivityYesterday && ledActivityToday) ||
                (occupancyYesterday && occupancyToday) ||
                (noiseYesterday && noiseToday);
    }

    /**
     * LED 센서 중 하나라도 활성화되어 있는지 확인
     */
    private boolean isAnyLedActive(Map<String, Object> hourData) {
        return getBooleanValue(hourData, "led_master_room") ||
                getBooleanValue(hourData, "led_living_room") ||
                getBooleanValue(hourData, "led_kitchen") ||
                getBooleanValue(hourData, "led_toilet");
    }

    /**
     * Boolean 값 안전하게 추출
     */
    private boolean getBooleanValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        return false;
    }

    /**
     * 공통 데이터 비율에 따른 위험도 결정
     */
    private String determineRiskLevel(double commonDataRatio) {
        if (commonDataRatio > NORMAL_THRESHOLD) {
            return "정상";
        } else if (commonDataRatio <= CRITICAL_THRESHOLD) {
            return "심각";
        } else {
            return "의심";
        }
    }

    /**
     * 가구 분석용 프롬프트 생성
     */
    private String buildHouseholdAnalysisPrompt(HouseholdComparisonDto comparisonData,
                                                double commonDataRatio, String riskLevel) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("독거노인 가구의 IoT 센서 데이터를 분석하여 위험 상황을 판단해주세요.\n\n");

        prompt.append("### 분석 개요:\n");
        prompt.append(String.format("- 가구 ID: %d\n", comparisonData.getHouseholdId()));
        prompt.append(String.format("- 어제 대비 오늘 공통 활동 비율: %.1f%%\n", commonDataRatio));
        prompt.append(String.format("- 위험도 판정: %s\n", riskLevel));
        prompt.append("- 판정 기준: 60% 초과(정상), 40-60%(의심), 40% 이하(심각)\n\n");

        prompt.append("### 센서 종류:\n");
        prompt.append("1. LED 센서: 방별 조명 사용 패턴 (안방, 거실, 주방, 화장실)\n");
        prompt.append("2. 재실 감지 센서: 움직임 감지 여부\n");
        prompt.append("3. 소음 감지 센서: 생활 소음 감지 여부\n\n");

        // 어제 데이터 요약
        prompt.append("### 어제 활동 패턴:\n");
        appendDailyActivitySummary(prompt, comparisonData.getYesterdayData(), "어제");

        // 오늘 데이터 요약
        prompt.append("### 오늘 활동 패턴:\n");
        appendDailyActivitySummary(prompt, comparisonData.getTodayData(), "오늘");

        // 시간대별 상세 비교 (주요 시간대만)
        prompt.append("### 주요 시간대별 비교:\n");
        appendHourlyComparison(prompt, comparisonData);

        prompt.append("위의 데이터를 종합적으로 분석하여 다음 형식으로 JSON 응답을 생성해주세요:\n");
        prompt.append("{\n");
        prompt.append("  \"riskLevel\": \"위험도 수준 (정상/의심/심각)\",\n");
        prompt.append("  \"situation\": \"현재 상황에 대한 상세한 분석 (활동 패턴 변화, 우려사항 포함)\",\n");
        prompt.append("  \"location\": \"주요 위험 감지 위치 (안방/거실/주방/화장실 등)\",\n");
        prompt.append("  \"comparisonDetails\": \"어제 대비 오늘의 구체적인 변화 수치와 패턴\",\n");
        prompt.append("  \"recommendation\": \"구체적인 대응 지침 (줄바꿈은 \\n으로 표시)\",\n");
        prompt.append("  \"reportingAgency\": \"신고 기관 (119/112/지역복지센터)\",\n");
        prompt.append("  \"contactNumber\": \"연락처\",\n");
        prompt.append("  \"urgencyLevel\": \"긴급도 (즉시/신속/보통/경과관찰)\"\n");
        prompt.append("}\n");

        return prompt.toString();
    }

    /**
     * 신고용 문서 생성 프롬프트
     */
    private String buildReportingPrompt(HouseholdComparisonDto comparisonData, AlertResponse initialAnalysis) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("독거노인 가구의 위험 상황에 대한 공식적인 상황 보고서를 작성해주세요.\n\n");

        prompt.append("### 기본 정보:\n");
        prompt.append(String.format("- 가구 ID: %d\n", comparisonData.getHouseholdId()));
        prompt.append(String.format("- 보고 일시: %s\n", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        prompt.append(String.format("- 위험도: %s\n", initialAnalysis.getRiskLevel()));
        prompt.append(String.format("- 활동 패턴 일치율: %.1f%%\n\n", initialAnalysis.getCommonDataRatio()));

        prompt.append("### 초기 분석 결과:\n");
        prompt.append(String.format("- 상황: %s\n", initialAnalysis.getSituation()));
        prompt.append(String.format("- 발생 위치: %s\n", initialAnalysis.getLocation()));
        prompt.append(String.format("- 비교 분석: %s\n\n", initialAnalysis.getComparisonDetails()));

        prompt.append("아래 형식으로 공식적인 보고서를 작성해주세요:\n");
        prompt.append("{\n");
        prompt.append("  \"reportTitle\": \"독거노인 안전 상황 보고서\",\n");
        prompt.append("  \"summary\": \"상황 요약 (한 줄로)\",\n");
        prompt.append("  \"detailedSituation\": \"상세 상황 설명 (시간순서, 구체적 수치 포함)\",\n");
        prompt.append("  \"riskAssessment\": \"위험도 평가 및 근거\",\n");
        prompt.append("  \"immediateActions\": \"즉시 필요한 조치사항\",\n");
        prompt.append("  \"followUpPlan\": \"후속 대응 계획\",\n");
        prompt.append("  \"contactInfo\": \"담당자 연락처 및 신고 기관 정보\",\n");
        prompt.append("  \"reportingAgency\": \"신고 대상 기관\",\n");
        prompt.append("  \"urgencyLevel\": \"긴급도\"\n");
        prompt.append("}\n");

        return prompt.toString();
    }

    /**
     * 일간 활동 요약 추가
     */
    private void appendDailyActivitySummary(StringBuilder prompt, List<Map<String, Object>> dayData, String dayLabel) {
        if (dayData.isEmpty()) {
            prompt.append(String.format("%s: 데이터 없음\n\n", dayLabel));
            return;
        }

        long ledActiveHours = dayData.stream().mapToLong(hour -> isAnyLedActive(hour) ? 1 : 0).sum();
        long occupancyHours = dayData.stream().mapToLong(hour -> getBooleanValue(hour, "is_occupied") ? 1 : 0).sum();
        long noiseHours = dayData.stream().mapToLong(hour -> getBooleanValue(hour, "is_noisy") ? 1 : 0).sum();

        prompt.append(String.format("%s 활동 요약 (총 %d시간 데이터):\n", dayLabel, dayData.size()));
        prompt.append(String.format("- 조명 사용: %d시간\n", ledActiveHours));
        prompt.append(String.format("- 움직임 감지: %d시간\n", occupancyHours));
        prompt.append(String.format("- 소음 감지: %d시간\n\n", noiseHours));
    }

    /**
     * 시간대별 비교 추가
     */
    private void appendHourlyComparison(StringBuilder prompt, HouseholdComparisonDto comparisonData) {
        List<Map<String, Object>> yesterdayData = comparisonData.getYesterdayData();
        List<Map<String, Object>> todayData = comparisonData.getTodayData();

        // 주요 시간대만 비교 (아침 7-9시, 점심 12-14시, 저녁 18-20시)
        int[] importantHours = {7, 8, 12, 13, 18, 19};

        for (int hour : importantHours) {
            if (hour < yesterdayData.size() && hour < todayData.size()) {
                Map<String, Object> yesterdayHour = yesterdayData.get(hour);
                Map<String, Object> todayHour = todayData.get(hour);

                prompt.append(String.format("%d시:\n", hour));
                prompt.append(String.format("  어제: LED=%s, 재실=%s, 소음=%s\n",
                        isAnyLedActive(yesterdayHour) ? "ON" : "OFF",
                        getBooleanValue(yesterdayHour, "is_occupied") ? "감지" : "미감지",
                        getBooleanValue(yesterdayHour, "is_noisy") ? "감지" : "미감지"));
                prompt.append(String.format("  오늘: LED=%s, 재실=%s, 소음=%s\n",
                        isAnyLedActive(todayHour) ? "ON" : "OFF",
                        getBooleanValue(todayHour, "is_occupied") ? "감지" : "미감지",
                        getBooleanValue(todayHour, "is_noisy") ? "감지" : "미감지"));
            }
        }
        prompt.append("\n");
    }

    /**
     * AWS Bedrock 호출
     */
    private String callBedrock(String prompt) {
        try {
            logger.debug("Bedrock 호출 시작 - 모델: {}", modelId);

            Message userMessage = Message.builder()
                    .role(ConversationRole.USER)
                    .content(ContentBlock.fromText(prompt))
                    .build();

            ConverseRequest request = ConverseRequest.builder()
                    .modelId(inferenceProfileArn != null ? inferenceProfileArn : modelId)
                    .messages(userMessage)
                    .build();

            ConverseResponse response = client.converse(request);
            String responseText = response.output().message().content().get(0).text();

            logger.debug("Bedrock 응답 길이: {} characters", responseText.length());

            return responseText;

        } catch (Exception e) {
            logger.error("Bedrock 호출 실패", e);
            throw new RuntimeException("AI 분석 서비스 호출 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Bedrock 응답 파싱
     */
    private AlertResponse parseBedrockResponse(String responseText) {
        try {
            // JSON 부분만 추출 (Bedrock이 추가 텍스트를 포함할 수 있음)
            int startIndex = responseText.indexOf("{");
            int endIndex = responseText.lastIndexOf("}") + 1;

            if (startIndex != -1 && endIndex > startIndex) {
                String jsonPart = responseText.substring(startIndex, endIndex);
                logger.debug("JSON 파싱 시도: {}", jsonPart.length() + " characters");

                AlertResponse response = objectMapper.readValue(jsonPart, AlertResponse.class);
                logger.info("Bedrock 응답 파싱 성공");
                return response;
            }

            logger.warn("JSON 형식을 찾을 수 없음, 기본 응답 생성");
            return createDefaultResponse(responseText);

        } catch (Exception e) {
            logger.error("Bedrock 응답 파싱 중 오류", e);
            return createDefaultResponse(responseText);
        }
    }

    /**
     * AI 분석 로그 기록
     */
    private void logAIAnalysis(int householdId, String requestType, String requestData,
                               String aiResponse, long processingTime, boolean success, String errorMessage) {
        try {
            // JSON 형태로 변환
            ObjectMapper objectMapper = new ObjectMapper();

            String jsonRequestData = requestData != null ?
                    objectMapper.writeValueAsString(Map.of("prompt", requestData)) : null;
            String jsonAiResponse = aiResponse != null ?
                    objectMapper.writeValueAsString(Map.of("response", aiResponse)) : null;

            String logQuery = "INSERT INTO ai_analysis_log " +
                    "(household_id, request_type, request_data, ai_response, processing_time_ms, success, error_message) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";

            jdbcTemplate.update(logQuery,
                    householdId,
                    requestType,
                    jsonRequestData,    // JSON 형태
                    jsonAiResponse,     // JSON 형태
                    processingTime,
                    success,
                    errorMessage);

            logger.debug("AI 분석 로그 기록 완료: householdId={}, type={}", householdId, requestType);

        } catch (Exception e) {
            logger.error("AI 분석 로그 기록 실패", e);
            // 로그 기록 실패는 메인 기능에 영향을 주지 않도록 무시
        }
    }
    /**
     * 기본 응답 생성
     */
    private AlertResponse createDefaultResponse(String text) {
        AlertResponse response = new AlertResponse();
        response.setRiskLevel("높음");
        response.setSituation("센서 데이터 분석 결과 이상 징후가 감지되었습니다.");
        response.setRecommendation(text != null && text.length() > 50 ? text.substring(0, 50) + "..." : "시스템 관리자에게 문의하세요.");
        response.setReportingAgency("119");
        response.setContactNumber("119");
        response.setUrgencyLevel("신속");
        return response;
    }

    /**
     * 안전 상태 응답
     */
    private AlertResponse createSafeResponse(double commonDataRatio) {
        AlertResponse response = new AlertResponse();
        response.setRiskLevel("정상");
        response.setSituation(String.format("어제 대비 %.1f%%의 공통 활동 패턴이 감지되어 정상 상태입니다.", commonDataRatio));
        response.setRecommendation("현재 특별한 조치가 필요하지 않습니다. 지속적인 모니터링을 계속합니다.");
        response.setCommonDataRatio(commonDataRatio);
        response.setLocation("전체");
        response.setComparisonDetails(String.format("공통 활동 비율 %.1f%%로 정상 범위입니다.", commonDataRatio));
        response.setReportingAgency("지역복지센터");
        response.setContactNumber("지역복지센터");
        response.setUrgencyLevel("경과관찰");
        return response;
    }

    /**
     * 오류 응답
     */
    private AlertResponse createErrorResponse() {
        AlertResponse response = new AlertResponse();
        response.setRiskLevel("확인필요");
        response.setSituation("AI 분석 중 오류가 발생했습니다.");
        response.setRecommendation("시스템 관리자에게 문의하세요.");
        response.setReportingAgency("시스템관리자");
        response.setContactNumber("내부문의");
        response.setUrgencyLevel("보통");
        return response;
    }

    /**
     * 데이터 부족 응답
     */
    private AlertResponse createInsufficientDataResponse() {
        AlertResponse response = new AlertResponse();
        response.setRiskLevel("확인필요");
        response.setSituation("비교할 센서 데이터가 충분하지 않습니다.");
        response.setRecommendation("센서 연결 상태를 확인하고 데이터 수집을 재시도해주세요.");
        response.setLocation("시스템");
        response.setComparisonDetails("데이터 부족으로 비교 분석 불가");
        response.setReportingAgency("시스템관리자");
        response.setContactNumber("내부문의");
        response.setUrgencyLevel("보통");
        return response;
    }
}