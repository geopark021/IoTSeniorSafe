package com.mcg.iotseniorsafe.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

// 안쓰는 기능 - > 어제, 오늘 데이터 공통 데이터 비율로 체킹 변경
// 1시간 주기 분석 스케줄러 - 위험 의심 내역에 띄울 행 정보 추출

@Component
@RequiredArgsConstructor
public class RiskAnalysisJob {

    private final JdbcTemplate jdbc;
    private final ReportService reportService;

    // 매 정시 10분 실행
    @Scheduled(cron = "0 10 * * * *")
    public void hourlyScan(){
        // sensor_summary_* 테이블 목록
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
                reportService.createAutoReport(
                        hhId,
                        (byte)(cnt>=5?2:1),    // 1:MED, 2:HIGH
                        "무점유·소음 패턴 "+cnt+"회"
                );
            }
        }
    }
}
