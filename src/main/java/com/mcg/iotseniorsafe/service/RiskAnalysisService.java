package com.mcg.iotseniorsafe.service;

import com.mcg.iotseniorsafe.dto.RiskEntryDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.*;

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
     * 위험 의심 내역 조회 (Secondary DB의 report 테이블 + JOIN)
     */
    public List<RiskEntryDto> getRiskEntries(int page, int size, String search, String sort) {
        logger.info("getRiskEntries 호출 - page: {}, size: {}, search: '{}', sort: '{}'", page, size, search, sort);

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT r.report_id, r.household_id, r.manager_id, m.name as manager_name, ");
        sql.append("h.name as household_name, h.address, h.contact_number, ");
        sql.append("r.status_code, r.created_at, r.updated_at, r.agency_name, ");
        sql.append("rd.description ");
        sql.append("FROM report r ");
        sql.append("LEFT JOIN manager m ON r.manager_id = m.manager_id ");
        sql.append("LEFT JOIN household h ON r.household_id = h.household_id ");
        sql.append("LEFT JOIN report_detail rd ON r.report_id = rd.report_id ");
        sql.append("WHERE 1=1 ");

        // 파라미터 리스트 생성
        List<Object> params = new ArrayList<>();

        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (m.name LIKE ? OR h.name LIKE ? OR h.address LIKE ?) ");
            String searchParam = "%" + search.trim() + "%";
            params.add(searchParam);
            params.add(searchParam);
            params.add(searchParam);
        }

        // 정렬
        if ("latest".equals(sort)) {
            sql.append("ORDER BY r.created_at DESC ");
        } else {
            sql.append("ORDER BY r.created_at ASC ");
        }

        sql.append("LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);

        logger.debug("실행할 SQL: {}", sql.toString());
        logger.debug("파라미터: {}", params);

        // Secondary DB 사용 (우리가 만든 신고 관련 테이블들)
        return secondaryJdbcTemplate.query(sql.toString(), (ResultSet rs, int rowNum) -> {
            RiskEntryDto dto = new RiskEntryDto();
            dto.setReportId(rs.getInt("report_id"));
            dto.setHouseholdId(rs.getInt("household_id"));
            dto.setManagerId(rs.getInt("manager_id"));
            dto.setManagerName(rs.getString("manager_name"));
            dto.setHouseholdName(rs.getString("household_name"));
            dto.setAddress(rs.getString("address"));
            dto.setContactNumber(rs.getString("contact_number"));
            dto.setStatusCode(rs.getInt("status_code"));
            dto.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            dto.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
            dto.setAgencyName(rs.getString("agency_name"));
            dto.setDescription(rs.getString("description"));

            // 실시간으로 공통 데이터 비율 계산
            double commonDataRatio = calculateCommonDataRatio(rs.getInt("household_id"));
            dto.setCommonDataRatio(commonDataRatio);
            dto.setRiskLevel(determineRiskLevel(commonDataRatio));

            return dto;
        }, params.toArray());
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
            // 어제와 오늘 데이터 개수 조회
            String yesterdayCountQuery = String.format(
                    "SELECT COUNT(*) FROM %s WHERE DATE(recorded_at) = DATE(NOW() - INTERVAL 1 DAY)", tableName);
            String todayCountQuery = String.format(
                    "SELECT COUNT(*) FROM %s WHERE DATE(recorded_at) = DATE(NOW())", tableName);

            Integer yesterdayCount = secondaryJdbcTemplate.queryForObject(yesterdayCountQuery, Integer.class);
            Integer todayCount = secondaryJdbcTemplate.queryForObject(todayCountQuery, Integer.class);

            if (yesterdayCount == null || todayCount == null || yesterdayCount == 0 || todayCount == 0) {
                logger.debug("프로토타입 데이터 부족 - householdId: {}, 어제: {}, 오늘: {}", householdId, yesterdayCount, todayCount);
                return 0.0;
            }

            // 간단한 비율 계산 (실제로는 더 복잡한 로직 필요)
            int minCount = Math.min(yesterdayCount, todayCount);
            int maxCount = Math.max(yesterdayCount, todayCount);

            double ratio = maxCount > 0 ? (double) minCount / maxCount * 100 : 0.0;
            logger.debug("프로토타입 센서 데이터 비율 계산 완료 - householdId: {}, 비율: {}%", householdId, ratio);
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
            // 어제와 오늘 데이터 개수 조회
            String yesterdayCountQuery = String.format(
                    "SELECT COUNT(*) FROM %s WHERE DATE(recorded_at) = DATE(NOW() - INTERVAL 1 DAY)", tableName);
            String todayCountQuery = String.format(
                    "SELECT COUNT(*) FROM %s WHERE DATE(recorded_at) = DATE(NOW())", tableName);

            Integer yesterdayCount = primaryJdbcTemplate.queryForObject(yesterdayCountQuery, Integer.class);
            Integer todayCount = primaryJdbcTemplate.queryForObject(todayCountQuery, Integer.class);

            if (yesterdayCount == null || todayCount == null || yesterdayCount == 0 || todayCount == 0) {
                logger.debug("기존 LED 데이터 부족 - householdId: {}, 어제: {}, 오늘: {}", householdId, yesterdayCount, todayCount);
                return 0.0;
            }

            // 간단한 비율 계산 (실제로는 더 복잡한 로직 필요)
            int minCount = Math.min(yesterdayCount, todayCount);
            int maxCount = Math.max(yesterdayCount, todayCount);

            double ratio = maxCount > 0 ? (double) minCount / maxCount * 100 : 0.0;
            logger.debug("기존 LED 데이터 비율 계산 완료 - householdId: {}, 비율: {}%", householdId, ratio);
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
     * 모든 가구의 위험도 평가 (Secondary와 Primary 모두 확인)
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
                int householdId = Integer.parseInt(householdIdStr);

                try {
                    double commonDataRatio = calculateCommonDataRatio(householdId);
                    String riskLevel = determineRiskLevel(commonDataRatio);

                    if ("의심".equals(riskLevel) || "심각".equals(riskLevel)) {
                        riskHouseholds++;

                        // 위험한 가구는 자동으로 report 테이블에 등록 (Secondary DB)
                        createAutomaticReport(householdId, riskLevel, commonDataRatio);
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

    /**
     * 위험 가구에 대한 자동 신고 생성 (Secondary DB에 저장)
     */
    private void createAutomaticReport(int householdId, String riskLevel, double commonDataRatio) {
        try {
            // 오늘 이미 신고된 가구인지 확인 (Secondary DB)
            String checkQuery = "SELECT COUNT(*) FROM report WHERE household_id = ? AND DATE(created_at) = DATE(NOW())";
            Integer existingReports = secondaryJdbcTemplate.queryForObject(checkQuery, Integer.class, householdId);

            if (existingReports != null && existingReports > 0) {
                logger.debug("이미 신고된 가구: householdId={}", householdId);
                return; // 이미 신고됨
            }

            // 시스템 자동 담당자 조회 (manager_id = 1이라고 가정)
            int systemManagerId = 1;

            // 신고 기관 결정
            String agencyName = "심각".equals(riskLevel) ? "119소방서" : "지역복지센터";

            // report 테이블에 삽입 (Secondary DB)
            String insertReportQuery = "INSERT INTO report (manager_id, household_id, status_code, created_at, updated_at, agency_name) " +
                    "VALUES (?, ?, ?, NOW(), NOW(), ?)";

            secondaryJdbcTemplate.update(insertReportQuery, systemManagerId, householdId, 0, agencyName);

            // 생성된 report_id 조회
            String getReportIdQuery = "SELECT LAST_INSERT_ID()";
            Integer reportId = secondaryJdbcTemplate.queryForObject(getReportIdQuery, Integer.class);

            // report_detail 테이블에 상세 내용 삽입 (Secondary DB)
            String description = String.format("시스템 자동 감지: %s 위험도, 공통 활동 비율 %.1f%%", riskLevel, commonDataRatio);
            String insertDetailQuery = "INSERT INTO report_detail (report_id, description) VALUES (?, ?)";

            secondaryJdbcTemplate.update(insertDetailQuery, reportId, description);

            logger.info("자동 신고 생성 완료: householdId={}, riskLevel={}, reportId={}", householdId, riskLevel, reportId);

        } catch (Exception e) {
            logger.error("자동 신고 생성 실패: householdId={}", householdId, e);
        }
    }
}