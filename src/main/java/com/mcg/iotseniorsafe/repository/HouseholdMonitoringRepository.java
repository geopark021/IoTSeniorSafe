package com.mcg.iotseniorsafe.repository;

import com.mcg.iotseniorsafe.dto.HouseholdMonitoringDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class HouseholdMonitoringRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 모든 가구의 모니터링 데이터 조회
     */
    public List<HouseholdMonitoringDto> findAllHouseholdMonitoringData() {
        String sql = """
            SELECT 
                h.household_id,
                h.name,
                h.contact_number,
                h.address,
                m.name as manager_name,
                m.contact_number as manager_contact,
                -- 최신 센서 데이터 조회 서브쿼리
                (SELECT COUNT(*) FROM all_household_sensor_log asl1 
                 WHERE asl1.household_id = h.household_id 
                   AND asl1.led_sensor_gbn = '01' 
                   AND asl1.recorded_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR)) as light_count,
                (SELECT COUNT(*) FROM all_household_sensor_log asl2 
                 WHERE asl2.household_id = h.household_id 
                   AND asl2.ocpy_sensor_gbn IS NOT NULL 
                   AND asl2.recorded_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR)) as occupancy_count,
                (SELECT COUNT(*) FROM all_household_sensor_log asl3 
                 WHERE asl3.household_id = h.household_id 
                   AND asl3.noise_sensor_gbn IS NOT NULL 
                   AND asl3.recorded_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR)) as noise_count,
                (SELECT COUNT(*) FROM all_household_sensor_log asl4 
                 WHERE asl4.household_id = h.household_id 
                   AND asl4.led_sensor_gbn = '04' 
                   AND asl4.recorded_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR)) as toilet_count,
                -- 마지막 활동 시간
                (SELECT MAX(recorded_at) FROM all_household_sensor_log asl5 
                 WHERE asl5.household_id = h.household_id) as last_activity_time,
                -- 최근 신고 상태
                (SELECT CASE 
                    WHEN COUNT(*) > 0 THEN '이상 없음'
                    ELSE '정상 진행 감지'
                 END
                 FROM report r 
                 WHERE r.household_id = h.household_id 
                   AND r.created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)) as status
            FROM household h
            LEFT JOIN manager m ON h.manager_id = m.manager_id
            ORDER BY last_activity_time DESC
            """;

        return jdbcTemplate.query(sql, new HouseholdMonitoringRowMapper());
    }

    /**
     * 특정 가구의 센서 요약 데이터 조회 (동적 테이블)
     */
    public List<HouseholdMonitoringDto> findHouseholdSensorSummary(Integer householdId) {
        String tableName = "sensor_summary_" + householdId;

        // 테이블 존재 여부 확인
        String checkTableSql = """
            SELECT COUNT(*) FROM information_schema.tables 
            WHERE table_schema = DATABASE() 
            AND table_name = ?
            """;

        Integer tableExists = jdbcTemplate.queryForObject(checkTableSql, Integer.class, tableName);

        if (tableExists == 0) {
            return List.of(); // 테이블이 없으면 빈 리스트 반환
        }

        String sql = String.format("""
            SELECT 
                %d as household_id,
                recorded_at,
                led_master_room,
                led_living_room, 
                led_kitchen,
                led_toilet,
                is_occupied,
                is_noisy
            FROM %s 
            WHERE recorded_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
            ORDER BY recorded_at DESC
            LIMIT 100
            """, householdId, tableName);

        return jdbcTemplate.query(sql, new SensorSummaryRowMapper());
    }

    private static class HouseholdMonitoringRowMapper implements RowMapper<HouseholdMonitoringDto> {
        @Override
        public HouseholdMonitoringDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            return HouseholdMonitoringDto.builder()
                    .householdId(rs.getInt("household_id"))
                    .name(rs.getString("name"))
                    .contactNumber(rs.getString("contact_number"))
                    .address(rs.getString("address"))
                    .managerName(rs.getString("manager_name"))
                    .managerContact(rs.getString("manager_contact"))
                    .lightLevel(rs.getDouble("light_count") / 10.0) // 정규화된 값
                    .occupancyLevel(rs.getDouble("occupancy_count") / 10.0)
                    .noiseLevel(rs.getDouble("noise_count") / 10.0)
                    .toiletLevel(rs.getDouble("toilet_count") / 10.0)
                    .lastActivityTime(rs.getTimestamp("last_activity_time") != null ?
                            rs.getTimestamp("last_activity_time").toLocalDateTime() : null)
                    .status(rs.getString("status"))
                    .sortTime(rs.getTimestamp("last_activity_time") != null ?
                            rs.getTimestamp("last_activity_time").toLocalDateTime() : LocalDateTime.MIN)
                    .build();
        }
    }

    private static class SensorSummaryRowMapper implements RowMapper<HouseholdMonitoringDto> {
        @Override
        public HouseholdMonitoringDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            return HouseholdMonitoringDto.builder()
                    .householdId(rs.getInt("household_id"))
                    .lightLevel(rs.getDouble("led_master_room"))
                    .occupancyLevel(rs.getDouble("led_living_room"))
                    .noiseLevel(rs.getDouble("led_kitchen"))
                    .toiletLevel(rs.getDouble("led_toilet"))
                    .lastActivityTime(rs.getTimestamp("recorded_at").toLocalDateTime())
                    .build();
        }
    }
}