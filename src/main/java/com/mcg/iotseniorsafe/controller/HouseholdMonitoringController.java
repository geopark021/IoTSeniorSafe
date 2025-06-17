package com.mcg.iotseniorsafe.controller;

import com.mcg.iotseniorsafe.dto.HouseholdMonitoringDto;
import com.mcg.iotseniorsafe.service.HouseholdMonitoringService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/monitoring")
public class HouseholdMonitoringController {

    @Autowired
    private HouseholdMonitoringService householdMonitoringService;

    /**
     * 통합 센서 모니터링 데이터 조회
     * GET /api/monitoring/households
     */
    @GetMapping("/households")
    public ResponseEntity<Map<String, Object>> getAllHouseholdMonitoring(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "latest") String sortBy) {

        try {
            List<HouseholdMonitoringDto> allData =
                    householdMonitoringService.getAllHouseholdMonitoringData();

            // 정렬 적용
            List<HouseholdMonitoringDto> sortedData = applySorting(allData, sortBy);

            // 페이징 적용
            List<HouseholdMonitoringDto> pagedData = applyPaging(sortedData, page, size);

            Map<String, Object> response = new HashMap<>();
            response.put("data", pagedData);
            response.put("totalElements", allData.size());
            response.put("totalPages", (int) Math.ceil((double) allData.size() / size));
            response.put("currentPage", page);
            response.put("pageSize", size);
            response.put("success", true);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "모니터링 데이터 조회 중 오류가 발생했습니다: " + e.getMessage());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 특정 가구의 상세 센서 데이터 조회
     * GET /api/monitoring/households/{householdId}/details
     */
    @GetMapping("/households/{householdId}/details")
    public ResponseEntity<Map<String, Object>> getHouseholdDetails(@PathVariable Integer householdId) {
        try {
            List<HouseholdMonitoringDto> details =
                    householdMonitoringService.getHouseholdSensorDetails(householdId);

            Map<String, Object> response = new HashMap<>();
            response.put("data", details);
            response.put("householdId", householdId);
            response.put("success", true);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "가구 상세 데이터 조회 중 오류가 발생했습니다: " + e.getMessage());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 실시간 모니터링 상태 조회 (WebSocket 대신 폴링용)
     * GET /api/monitoring/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getMonitoringStatus() {
        try {
            List<HouseholdMonitoringDto> allData =
                    householdMonitoringService.getAllHouseholdMonitoringData();

            // 상태별 통계 계산
            long normalCount = allData.stream()
                    .filter(d -> "정상 진행 감지".equals(d.getStatus()))
                    .count();

            long noIssueCount = allData.stream()
                    .filter(d -> "이상 없음".equals(d.getStatus()))
                    .count();

            Map<String, Object> status = new HashMap<>();
            status.put("totalHouseholds", allData.size());
            status.put("normalCount", normalCount);
            status.put("noIssueCount", noIssueCount);
            status.put("lastUpdated", java.time.LocalDateTime.now());
            status.put("success", true);

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "모니터링 상태 조회 중 오류가 발생했습니다: " + e.getMessage());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 정렬 적용
     */
    private List<HouseholdMonitoringDto> applySorting(List<HouseholdMonitoringDto> data, String sortBy) {
        return switch (sortBy) {
            case "latest" -> data.stream()
                    .sorted((a, b) -> b.getSortTime().compareTo(a.getSortTime()))
                    .toList();
            case "name" -> data.stream()
                    .sorted((a, b) -> a.getName().compareTo(b.getName()))
                    .toList();
            case "status" -> data.stream()
                    .sorted((a, b) -> a.getStatus().compareTo(b.getStatus()))
                    .toList();
            default -> data;
        };
    }

    /**
     * 페이징 적용
     */
    private List<HouseholdMonitoringDto> applyPaging(List<HouseholdMonitoringDto> data, int page, int size) {
        int start = page * size;
        int end = Math.min(start + size, data.size());

        if (start >= data.size()) {
            return List.of();
        }

        return data.subList(start, end);
    }
}