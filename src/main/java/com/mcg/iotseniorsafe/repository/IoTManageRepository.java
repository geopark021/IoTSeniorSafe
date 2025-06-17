package com.mcg.iotseniorsafe.repository;

import com.mcg.iotseniorsafe.dto.MaintenanceLogDto;
import com.mcg.iotseniorsafe.dto.SensorStatsDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class IoTManageRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 센서 상태 통계 조회
     */
    public SensorStatsDto getSensorStats() {
        // 전체 가구 수
        String totalHouseholdsQuery = "SELECT COUNT(*) FROM household";
        Integer totalHouseholds = jdbcTemplate.queryForObject(totalHouseholdsQuery, Integer.class);

        // LED 센서 부착 가구 수
        String ledSensorQuery = """
            SELECT COUNT(DISTINCT household_id) 
            FROM all_household_sensor_log 
            WHERE led_sensor_gbn IS NOT NULL 
            AND recorded_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
            """;
        Integer ledSensorCount = jdbcTemplate.queryForObject(ledSensorQuery, Integer.class);

        // 재실 감지 센서 부착 가구 수
        String occupancySensorQuery = """
            SELECT COUNT(DISTINCT household_id) 
            FROM all_household_sensor_log 
            WHERE ocpy_sensor_gbn IS NOT NULL 
            AND recorded_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
            """;
        Integer occupancySensorCount = jdbcTemplate.queryForObject(occupancySensorQuery, Integer.class);

        // 소음 센서 부착 가구 수
        String noiseSensorQuery = """
            SELECT COUNT(DISTINCT household_id) 
            FROM all_household_sensor_log 
            WHERE noise_sensor_gbn IS NOT NULL 
            AND recorded_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
            """;
        Integer noiseSensorCount = jdbcTemplate.queryForObject(noiseSensorQuery, Integer.class);

        // LED 센서 오류 수
        String errorCountQuery = """
            SELECT COUNT(*) FROM household h
            WHERE NOT EXISTS (
                SELECT 1 FROM all_household_sensor_log asl
                WHERE asl.household_id = h.household_id
                AND asl.led_sensor_gbn IS NOT NULL
                AND asl.recorded_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
            )
            """;
        Integer errorCount = jdbcTemplate.queryForObject(errorCountQuery, Integer.class);

        return SensorStatsDto.builder()
                .totalHouseholds(totalHouseholds != null ? totalHouseholds : 0)
                .ledSensorCount(ledSensorCount != null ? ledSensorCount : 0)
                .occupancySensorCount(occupancySensorCount != null ? occupancySensorCount : 0)
                .noiseSensorCount(noiseSensorCount != null ? noiseSensorCount : 0)
                .errorCount(errorCount != null ? errorCount : 0)
                .build();
    }

    /**
     * 유지보수 로그 조회
     */
    public List<MaintenanceLogDto> getMaintenanceLogs(int page, int size, String sortBy) {
        // 정렬 조건 설정
        String orderBy = switch (sortBy) {
            case "name" -> "h.name ASC";
            case "status" -> "status ASC, last_activity DESC";
            default -> "last_activity DESC";
        };

        String query = String.format("""
            SELECT 
                ROW_NUMBER() OVER (ORDER BY %s) as row_num,
                h.household_id,
                h.name as household_name,
                h.address,
                'LED센서' as sensor_type,
                CASE 
                    WHEN MAX(asl.recorded_at) IS NULL THEN '데이터 없음'
                    WHEN MAX(asl.recorded_at) < DATE_SUB(NOW(), INTERVAL 24 HOUR) THEN '연결 끊김 (오프라인)'
                    WHEN MAX(asl.recorded_at) < DATE_SUB(NOW(), INTERVAL 12 HOUR) THEN '신호 약함'
                    ELSE '정상'
                END as error_message,
                CASE 
                    WHEN MAX(asl.recorded_at) IS NULL THEN '점검 필요'
                    WHEN MAX(asl.recorded_at) < DATE_SUB(NOW(), INTERVAL 24 HOUR) THEN '재부팅 필요'
                    WHEN MAX(asl.recorded_at) < DATE_SUB(NOW(), INTERVAL 12 HOUR) THEN '신호 확인 필요'
                    ELSE '정상'
                END as status,
                COALESCE(MAX(asl.recorded_at), NOW() - INTERVAL 7 DAY) as last_activity,
                DATE_FORMAT(COALESCE(MAX(asl.recorded_at), NOW() - INTERVAL 7 DAY), '%%Y.%%m.%%d %%H:%%i:%%s') as formatted_time
            FROM household h
            LEFT JOIN all_household_sensor_log asl ON h.household_id = asl.household_id 
                AND asl.led_sensor_gbn IS NOT NULL
            GROUP BY h.household_id, h.name, h.address
            HAVING error_message != '정상'
            ORDER BY %s
            LIMIT ? OFFSET ?
            """, orderBy, orderBy);

        return jdbcTemplate.query(query, new MaintenanceLogRowMapper(), size, page * size);
    }

    /**
     * 유지보수 로그 전체 개수 조회
     */
    public int getMaintenanceLogCount() {
        String countQuery = """
            SELECT COUNT(*) FROM (
                SELECT h.household_id
                FROM household h
                LEFT JOIN all_household_sensor_log asl ON h.household_id = asl.household_id 
                    AND asl.led_sensor_gbn IS NOT NULL
                GROUP BY h.household_id
                HAVING CASE 
                    WHEN MAX(asl.recorded_at) IS NULL THEN '데이터 없음'
                    WHEN MAX(asl.recorded_at) < DATE_SUB(NOW(), INTERVAL 24 HOUR) THEN '연결 끊김 (오프라인)'
                    WHEN MAX(asl.recorded_at) < DATE_SUB(NOW(), INTERVAL 12 HOUR) THEN '신호 약함'
                    ELSE '정상'
                END != '정상'
            ) as problem_households
            """;

        Integer count = jdbcTemplate.queryForObject(countQuery, Integer.class);
        return count != null ? count : 0;
    }

    /**
     * 가구 존재 여부 확인
     */
    public boolean existsHousehold(Integer householdId) {
        String query = "SELECT COUNT(*) FROM household WHERE household_id = ?";
        Integer count = jdbcTemplate.queryForObject(query, Integer.class, householdId);
        return count != null && count > 0;
    }

    /**
     * 유지보수 로그 RowMapper
     */
    private static class MaintenanceLogRowMapper implements RowMapper<MaintenanceLogDto> {
        @Override
        public MaintenanceLogDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            return MaintenanceLogDto.builder()
                    .id(rs.getInt("row_num"))
                    .householdId(rs.getInt("household_id"))
                    .householdName(rs.getString("household_name"))
                    .address(rs.getString("address"))
                    .sensorType(rs.getString("sensor_type"))
                    .errorMessage(rs.getString("error_message"))
                    .status(rs.getString("status"))
                    .timestamp(rs.getString("formatted_time"))
                    .lastActivity(rs.getTimestamp("last_activity") != null ?
                            rs.getTimestamp("last_activity").toLocalDateTime() : null)
                    .build();
        }
    }
}