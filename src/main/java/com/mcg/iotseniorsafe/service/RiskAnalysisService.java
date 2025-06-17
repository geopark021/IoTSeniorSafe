package com.mcg.iotseniorsafe.service;

import com.mcg.iotseniorsafe.dto.RiskEntryDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RiskAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(RiskAnalysisService.class);

    private final JdbcTemplate primaryJdbcTemplate;   // 한전 MCS (기존 LED만)
    private final JdbcTemplate secondaryJdbcTemplate; // 우리 시스템 (신고 테이블 + 프로토타입 센서)

    // 위험도 판단 기준값
    private static final double NORMAL_THRESHOLD = 60.0;
    private static final double CRITICAL_THRESHOLD = 40.0;

    @Autowired
    public RiskAnalysisService(@Qualifier("primaryJdbcTemplate") JdbcTemplate primaryJdbcTemplate,
                               @Qualifier("secondaryJdbcTemplate") JdbcTemplate secondaryJdbcTemplate) {
        this.primaryJdbcTemplate = primaryJdbcTemplate;
        this.secondaryJdbcTemplate = secondaryJdbcTemplate;
    }

    /**
     * 위험 의심 내역 조회 - 실시간 센서 데이터 기반 (report 테이블 사용 안함)
     */
    public List<RiskEntryDto> getRiskEntries(int page, int size, String search, String sort) {
        logger.info("getRiskEntries 호출 - page: {}, size: {}, search: '{}', sort: '{}'", page, size, search, sort);

        List<RiskEntryDto> riskEntries = new ArrayList<>();

        try {
            // 1. 모든 sensor_summary 테이블 조회 (Secondary + Primary)
            String tablesQuery = "SHOW TABLES LIKE 'sensor_summary_%'";
            List<String> secondaryTables = secondaryJdbcTemplate.queryForList(tablesQuery, String.class);
            List<String> primaryTables = primaryJdbcTemplate.queryForList(tablesQuery, String.class);

            Set<String> allTables = new HashSet<>();
            allTables.addAll(secondaryTables);
            allTables.addAll(primaryTables);

            // 2. 각 가구별 위험도 계산
            for (String tableName : allTables) {
                String householdIdStr = tableName.replace("sensor_summary_", "");

                // 숫자가 아닌 테이블명 필터링 (예: sensor_summary_queue_test)
                if (!householdIdStr.matches("\\d+")) {
                    logger.debug("숫자가 아닌 테이블 스킵: {}", tableName);
                    continue;
                }

                int householdId = Integer.parseInt(householdIdStr);

                try {
                    double commonDataRatio = calculateCommonDataRatio(householdId);
                    String riskLevel = determineRiskLevel(commonDataRatio);

                    // 3. 위험(의심, 심각)인 경우만 포함
                    if ("의심".equals(riskLevel) || "심각".equals(riskLevel)) {

                        // 4. 가구 정보 조회
                        String householdQuery = "SELECT h.household_id, h.name, h.address, h.contact_number, h.manager_id, " +
                                "m.name as manager_name FROM household h " +
                                "LEFT JOIN manager m ON h.manager_id = m.manager_id " +
                                "WHERE h.household_id = ?";

                        Map<String, Object> householdInfo = null;
                        try {
                            householdInfo = secondaryJdbcTemplate.queryForMap(householdQuery, householdId);
                        } catch (Exception e) {
                            logger.warn("가구 정보 조회 실패: householdId={}", householdId);
                            continue;
                        }

                        // 5. RiskEntryDto 생성 (report 테이블 없이)
                        RiskEntryDto dto = new RiskEntryDto();
                        dto.setReportId(0); // 아직 신고되지 않음
                        dto.setHouseholdId(householdId);
                        dto.setManagerId((Integer) householdInfo.get("manager_id"));
                        dto.setManagerName((String) householdInfo.get("manager_name"));
                        dto.setHouseholdName((String) householdInfo.get("name"));
                        dto.setAddress((String) householdInfo.get("address"));
                        dto.setContactNumber((String) householdInfo.get("contact_number"));
                        dto.setStatusCode(0); // 미처리
                        dto.setCreatedAt(LocalDateTime.now()); // 현재 시간
                        dto.setUpdatedAt(LocalDateTime.now());
                        dto.setAgencyName(riskLevel.equals("심각") ? "119소방서" : "지역복지센터");
                        dto.setDescription(String.format("시스템 감지: %s 위험도, 공통 활동 비율 %.1f%%",
                                riskLevel, commonDataRatio));
                        dto.setCommonDataRatio(commonDataRatio);
                        dto.setRiskLevel(riskLevel);

                        riskEntries.add(dto);
                    }
                } catch (Exception e) {
                    logger.warn("가구 {} 위험도 계산 실패: {}", householdId, e.getMessage());
                }
            }

            // 6. 정렬
            if ("latest".equals(sort)) {
                riskEntries.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
            } else {
                riskEntries.sort((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));
            }

            // 7. 검색 필터 적용
            if (search != null && !search.trim().isEmpty()) {
                String searchLower = search.toLowerCase();
                riskEntries = riskEntries.stream()
                        .filter(dto ->
                                (dto.getManagerName() != null && dto.getManagerName().toLowerCase().contains(searchLower)) ||
                                        (dto.getHouseholdName() != null && dto.getHouseholdName().toLowerCase().contains(searchLower)) ||
                                        (dto.getAddress() != null && dto.getAddress().toLowerCase().contains(searchLower))
                        )
                        .collect(Collectors.toList());
            }

            // 8. 페이징 적용
            int startIndex = page * size;
            int endIndex = Math.min(startIndex + size, riskEntries.size());

            if (startIndex < riskEntries.size()) {
                return riskEntries.subList(startIndex, endIndex);
            } else {
                return new ArrayList<>();
            }

        } catch (Exception e) {
            logger.error("위험 의심 내역 조회 실패", e);
            return new ArrayList<>();
        }
    }

    /**
     * 특정 가구의 공통 데이터 비율 계산
     * Secondary의 프로토타입 데이터 우선, 없으면 Primary의 기존 데이터 사용
     */
    private double calculateCommonDataRatio(int householdId) {
        try {
            String tableName = "sensor_summary_" + householdId;

            // 우선 Secondary에서 프로토타입 센서 데이터 확인
            String checkTableQuery = "SELECT COUNT(*) FROM information_schema.tables " +
                    "WHERE table_schema = DATABASE() AND table_name = ?";
            Integer tableExists = secondaryJdbcTemplate.queryForObject(checkTableQuery, Integer.class, tableName);

            if (tableExists != null && tableExists > 0) {
                // Secondary에 프로토타입 데이터가 있으면 사용 (LED + 재실감지 + 소음감지)
                logger.debug("프로토타입 센서 데이터 사용: householdId={}", householdId);
                return calculateFromSecondary(householdId, tableName);
            } else {
                // Secondary에 없으면 Primary에서 기존 LED 데이터 사용
                tableExists = primaryJdbcTemplate.queryForObject(checkTableQuery, Integer.class, tableName);
                if (tableExists != null && tableExists > 0) {
                    logger.debug("기존 LED 데이터 사용: householdId={}", householdId);
                    return calculateFromPrimary(householdId, tableName);
                }
            }

            logger.warn("센서 테이블이 존재하지 않음: {}", tableName);
            return 0.0;

        } catch (Exception e) {
            logger.error("공통 데이터 비율 계산 실패: householdId={}", householdId, e);
            return 0.0;
        }
    }

    /**
     * Secondary DB에서 프로토타입 센서 데이터 사용 (LED + 재실감지 + 소음감지)
     */
    private double calculateFromSecondary(int householdId, String tableName) {
        try {
            // 어제와 오늘의 시간대별 활동 데이터 조회
            String yesterdayQuery = String.format(
                    "SELECT HOUR(recorded_at) as hour, " +
                            "MAX(led_master_room + led_living_room + led_kitchen + led_toilet) as led_active, " +
                            "MAX(is_occupied) as occupied, " +
                            "MAX(is_noisy) as noisy " +
                            "FROM %s WHERE DATE(recorded_at) = DATE(NOW() - INTERVAL 1 DAY) " +
                            "GROUP BY HOUR(recorded_at) ORDER BY hour", tableName);

            String todayQuery = String.format(
                    "SELECT HOUR(recorded_at) as hour, " +
                            "MAX(led_master_room + led_living_room + led_kitchen + led_toilet) as led_active, " +
                            "MAX(is_occupied) as occupied, " +
                            "MAX(is_noisy) as noisy " +
                            "FROM %s WHERE DATE(recorded_at) = DATE(NOW()) " +
                            "GROUP BY HOUR(recorded_at) ORDER BY hour", tableName);

            List<Map<String, Object>> yesterdayData = secondaryJdbcTemplate.queryForList(yesterdayQuery);
            List<Map<String, Object>> todayData = secondaryJdbcTemplate.queryForList(todayQuery);

            if (yesterdayData.isEmpty() || todayData.isEmpty()) {
                logger.debug("프로토타입 데이터 부족 - householdId: {}, 어제: {}시간, 오늘: {}시간",
                        householdId, yesterdayData.size(), todayData.size());
                return 0.0;
            }

            // 시간대별 활동 패턴 비교
            int commonActivityHours = 0;
            int totalComparableHours = 0;

            // 어제 데이터를 Map으로 변환 (빠른 검색을 위해)
            Map<Integer, Map<String, Object>> yesterdayMap = new HashMap<>();
            for (Map<String, Object> row : yesterdayData) {
                yesterdayMap.put((Integer) row.get("hour"), row);
            }

            // 오늘 데이터와 비교
            for (Map<String, Object> todayRow : todayData) {
                Integer hour = (Integer) todayRow.get("hour");
                Map<String, Object> yesterdayRow = yesterdayMap.get(hour);

                if (yesterdayRow != null) {
                    totalComparableHours++;

                    // 같은 시간대의 활동 패턴 비교
                    boolean yesterdayLed = ((Number) yesterdayRow.get("led_active")).intValue() > 0;
                    boolean todayLed = ((Number) todayRow.get("led_active")).intValue() > 0;

                    boolean yesterdayOccupied = ((Number) yesterdayRow.get("occupied")).intValue() > 0;
                    boolean todayOccupied = ((Number) todayRow.get("occupied")).intValue() > 0;

                    boolean yesterdayNoisy = ((Number) yesterdayRow.get("noisy")).intValue() > 0;
                    boolean todayNoisy = ((Number) todayRow.get("noisy")).intValue() > 0;

                    // 하나라도 공통 활동이 있으면 카운트
                    if ((yesterdayLed && todayLed) ||
                            (yesterdayOccupied && todayOccupied) ||
                            (yesterdayNoisy && todayNoisy)) {
                        commonActivityHours++;
                    }
                }
            }

            double ratio = totalComparableHours > 0 ?
                    (double) commonActivityHours / totalComparableHours * 100 : 0.0;

            logger.debug("프로토타입 센서 활동 패턴 비교 - householdId: {}, 공통활동: {}/{}시간 = {}%",
                    householdId, commonActivityHours, totalComparableHours, ratio);
            return ratio;

        } catch (Exception e) {
            logger.error("프로토타입 센서 데이터 계산 실패: householdId={}", householdId, e);
            return 0.0;
        }
    }

    /**
     * Primary DB에서 기존 LED 데이터만 사용
     */
    private double calculateFromPrimary(int householdId, String tableName) {
        try {
            // 어제와 오늘의 시간대별 LED 활동 데이터 조회
            String yesterdayQuery = String.format(
                    "SELECT HOUR(recorded_at) as hour, " +
                            "MAX(led_master_room + led_living_room + led_kitchen + led_toilet) as led_active " +
                            "FROM %s WHERE DATE(recorded_at) = DATE(NOW() - INTERVAL 1 DAY) " +
                            "GROUP BY HOUR(recorded_at) ORDER BY hour", tableName);

            String todayQuery = String.format(
                    "SELECT HOUR(recorded_at) as hour, " +
                            "MAX(led_master_room + led_living_room + led_kitchen + led_toilet) as led_active " +
                            "FROM %s WHERE DATE(recorded_at) = DATE(NOW()) " +
                            "GROUP BY HOUR(recorded_at) ORDER BY hour", tableName);

            List<Map<String, Object>> yesterdayData = primaryJdbcTemplate.queryForList(yesterdayQuery);
            List<Map<String, Object>> todayData = primaryJdbcTemplate.queryForList(todayQuery);

            if (yesterdayData.isEmpty() || todayData.isEmpty()) {
                logger.debug("기존 LED 데이터 부족 - householdId: {}, 어제: {}시간, 오늘: {}시간",
                        householdId, yesterdayData.size(), todayData.size());
                return 0.0;
            }

            // 시간대별 LED 활동 패턴 비교
            int commonLedHours = 0;
            int totalComparableHours = 0;

            // 어제 데이터를 Map으로 변환
            Map<Integer, Map<String, Object>> yesterdayMap = new HashMap<>();
            for (Map<String, Object> row : yesterdayData) {
                yesterdayMap.put((Integer) row.get("hour"), row);
            }

            // 오늘 데이터와 비교
            for (Map<String, Object> todayRow : todayData) {
                Integer hour = (Integer) todayRow.get("hour");
                Map<String, Object> yesterdayRow = yesterdayMap.get(hour);

                if (yesterdayRow != null) {
                    totalComparableHours++;

                    // 같은 시간대의 LED 활동 비교
                    boolean yesterdayLed = ((Number) yesterdayRow.get("led_active")).intValue() > 0;
                    boolean todayLed = ((Number) todayRow.get("led_active")).intValue() > 0;

                    // 둘 다 LED 활동이 있으면 공통 활동
                    if (yesterdayLed && todayLed) {
                        commonLedHours++;
                    }
                }
            }

            double ratio = totalComparableHours > 0 ?
                    (double) commonLedHours / totalComparableHours * 100 : 0.0;

            logger.debug("기존 LED 활동 패턴 비교 - householdId: {}, 공통LED: {}/{}시간 = {}%",
                    householdId, commonLedHours, totalComparableHours, ratio);
            return ratio;

        } catch (Exception e) {
            logger.error("기존 LED 데이터 계산 실패: householdId={}", householdId, e);
            return 0.0;
        }
    }

    /**
     * 위험도 결정
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
     * 특정 가구의 센서 데이터 요약 조회 (Secondary DB 우선)
     */
    public Map<String, Object> getHouseholdSensorSummary(int householdId) {
        Map<String, Object> result = new HashMap<>();

        try {
            String tableName = "sensor_summary_" + householdId;

            // Secondary DB에서 우선 확인
            String checkTableQuery = "SELECT COUNT(*) FROM information_schema.tables " +
                    "WHERE table_schema = DATABASE() AND table_name = ?";
            Integer tableExists = secondaryJdbcTemplate.queryForObject(checkTableQuery, Integer.class, tableName);

            JdbcTemplate useTemplate = secondaryJdbcTemplate;
            String dataSource = "프로토타입 센서";

            if (tableExists == null || tableExists == 0) {
                // Secondary에 없으면 Primary 확인
                tableExists = primaryJdbcTemplate.queryForObject(checkTableQuery, Integer.class, tableName);
                if (tableExists != null && tableExists > 0) {
                    useTemplate = primaryJdbcTemplate;
                    dataSource = "기존 LED 센서";
                } else {
                    result.put("error", "센서 요약 테이블이 존재하지 않습니다: " + tableName);
                    return result;
                }
            }

            // 오늘 데이터 조회
            String todayQuery = String.format(
                    "SELECT COUNT(*) as total_hours, " +
                            "SUM(CASE WHEN (led_master_room + led_living_room + led_kitchen + led_toilet) > 0 THEN 1 ELSE 0 END) as led_active_hours, " +
                            "SUM(CASE WHEN is_occupied = 1 THEN 1 ELSE 0 END) as occupancy_hours, " +
                            "SUM(CASE WHEN is_noisy = 1 THEN 1 ELSE 0 END) as noise_hours " +
                            "FROM %s WHERE DATE(recorded_at) = DATE(NOW())", tableName);

            Map<String, Object> todayData = useTemplate.queryForMap(todayQuery);

            // 어제 데이터 조회
            String yesterdayQuery = String.format(
                    "SELECT COUNT(*) as total_hours, " +
                            "SUM(CASE WHEN (led_master_room + led_living_room + led_kitchen + led_toilet) > 0 THEN 1 ELSE 0 END) as led_active_hours, " +
                            "SUM(CASE WHEN is_occupied = 1 THEN 1 ELSE 0 END) as occupancy_hours, " +
                            "SUM(CASE WHEN is_noisy = 1 THEN 1 ELSE 0 END) as noise_hours " +
                            "FROM %s WHERE DATE(recorded_at) = DATE(NOW() - INTERVAL 1 DAY)", tableName);

            Map<String, Object> yesterdayData = useTemplate.queryForMap(yesterdayQuery);

            // 가구 정보 조회 (Secondary DB에서)
            String householdQuery = "SELECT h.name, h.address, h.contact_number, m.name as manager_name " +
                    "FROM household h LEFT JOIN manager m ON h.manager_id = m.manager_id " +
                    "WHERE h.household_id = ?";

            Map<String, Object> householdInfo = secondaryJdbcTemplate.queryForMap(householdQuery, householdId);

            result.put("householdId", householdId);
            result.put("householdInfo", householdInfo);
            result.put("dataSource", dataSource);
            result.put("today", todayData);
            result.put("yesterday", yesterdayData);
            result.put("commonDataRatio", calculateCommonDataRatio(householdId));
            result.put("timestamp", LocalDateTime.now());

        } catch (Exception e) {
            logger.error("가구 센서 데이터 요약 조회 실패: householdId={}", householdId, e);
            result.put("error", "데이터 조회 실패: " + e.getMessage());
        }

        return result;
    }

    /**
     * 모든 가구의 위험도 평가 (자동 신고 생성 제거)
     */
    public Map<String, Object> evaluateAllHouseholds() {
        Map<String, Object> result = new HashMap<>();

        try {
            // Secondary DB의 sensor_summary_ 테이블 목록 조회
            String tablesQuery = "SHOW TABLES LIKE 'sensor_summary_%'";
            List<String> secondaryTables = secondaryJdbcTemplate.queryForList(tablesQuery, String.class);

            // Primary DB의 sensor_summary_ 테이블 목록 조회
            List<String> primaryTables = primaryJdbcTemplate.queryForList(tablesQuery, String.class);

            // 모든 테이블 통합 (중복 제거)
            Set<String> allTables = new HashSet<>();
            allTables.addAll(secondaryTables);
            allTables.addAll(primaryTables);

            int totalHouseholds = allTables.size();
            int riskHouseholds = 0;

            for (String tableName : allTables) {
                String householdIdStr = tableName.replace("sensor_summary_", "");

                // 숫자가 아닌 테이블명 필터링 (예: sensor_summary_queue_test)
                if (!householdIdStr.matches("\\d+")) {
                    logger.debug("숫자가 아닌 테이블 스킵: {}", tableName);
                    continue;
                }

                int householdId = Integer.parseInt(householdIdStr);

                try {
                    double commonDataRatio = calculateCommonDataRatio(householdId);
                    String riskLevel = determineRiskLevel(commonDataRatio);

                    if ("의심".equals(riskLevel) || "심각".equals(riskLevel)) {
                        riskHouseholds++;

                        // 자동 신고 생성 제거 - 로그만 남기기
                        logger.info("위험 감지: householdId={}, riskLevel={}, ratio={}%",
                                householdId, riskLevel, commonDataRatio);
                    }

                } catch (Exception e) {
                    logger.warn("가구 {} 평가 중 오류: {}", householdId, e.getMessage());
                }
            }

            result.put("totalHouseholds", totalHouseholds);
            result.put("riskHouseholds", riskHouseholds);
            result.put("secondaryTables", secondaryTables.size());
            result.put("primaryTables", primaryTables.size());
            result.put("evaluationTime", LocalDateTime.now());
            result.put("status", "completed");

        } catch (Exception e) {
            logger.error("전체 가구 위험도 평가 실패", e);
            result.put("status", "failed");
            result.put("error", e.getMessage());
        }

        return result;
    }
}