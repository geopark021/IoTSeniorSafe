package com.mcg.iotseniorsafe.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

// 스케줄러 비활성화 - 자동 신고 생성하지 않음
@Component
@RequiredArgsConstructor
public class RiskAnalysisJob {

    private static final Logger logger = LoggerFactory.getLogger(RiskAnalysisJob.class);

    private final JdbcTemplate jdbc;
    private final ReportService reportService;

    // 스케줄러 비활성화 - 자동 신고 생성 중단
    // @Scheduled(cron = "0 10 * * * *")  // 주석 처리
    public void hourlyScan(){
        logger.info("스케줄러 비활성화됨 - 자동 신고 생성하지 않음");

        // 기존 로직은 유지하되 신고 생성은 하지 않음
        List<String> tbls = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables " +
                        "WHERE table_schema = DATABASE() AND table_name LIKE 'sensor_summary_%'",
                String.class);

        for(String tbl: tbls){
            int hhId = Integer.parseInt(tbl.replace("sensor_summary_", ""));
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM "+tbl+" WHERE recorded_at >= NOW() - INTERVAL 1 HOUR");

            long cnt = rows.stream()
                    .filter(r -> ((byte)r.get("is_occupied")==0) && ((byte)r.get("is_noisy")==1))
                    .count();

            if(cnt >= 3){
                // 자동 신고 생성하지 않고 로그만 기록
                logger.info("위험 패턴 감지 (신고 생성 안함): householdId={}, 패턴=무점유·소음 {}회", hhId, cnt);

                // 기존 자동 신고 생성 코드 주석 처리
                // reportService.createAutoReport(
                //         hhId,
                //         (byte)(cnt>=5?2:1),    // 1:MED, 2:HIGH
                //         "무점유·소음 패턴 "+cnt+"회"
                // );
            }
        }
    }
}