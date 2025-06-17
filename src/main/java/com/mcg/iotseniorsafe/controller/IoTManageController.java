// src/main/java/com/mcg/iotseniorsafe/controller/IoTManageController.java
package com.mcg.iotseniorsafe.controller;

import com.mcg.iotseniorsafe.dto.MaintenanceLogDto;
import com.mcg.iotseniorsafe.dto.SensorStatsDto;
import com.mcg.iotseniorsafe.service.IoTManageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/iot-manage")
public class IoTManageController {

    @Autowired
    private IoTManageService iotManageService;

    /**
     * IoT 센서 상태 통계 조회. 오류, 유지 보수 로그 
     * GET /api/iot-manage/sensor-stats
     */
    @GetMapping("/sensor-stats")
    public ResponseEntity<Map<String, Object>> getSensorStats() {
        try {
            SensorStatsDto stats = iotManageService.getSensorStats();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("totalHouseholds", stats.getTotalHouseholds());
            response.put("ledSensorCount", stats.getLedSensorCount());
            response.put("occupancySensorCount", stats.getOccupancySensorCount());
            response.put("noiseSensorCount", stats.getNoiseSensorCount());
            response.put("errorCount", stats.getErrorCount());
            response.put("lastUpdated", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "센서 상태 통계 조회 중 오류가 발생했습니다: " + e.getMessage());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 유지보수 로그 조회
     * GET /api/iot-manage/maintenance-logs
     */
    @GetMapping("/maintenance-logs")
    public ResponseEntity<Map<String, Object>> getMaintenanceLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "latest") String sortBy) {

        try {
            List<MaintenanceLogDto> logs = iotManageService.getMaintenanceLogs(page, size, sortBy);
            int totalElements = iotManageService.getMaintenanceLogCount();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", logs);
            response.put("totalElements", totalElements);
            response.put("totalPages", (int) Math.ceil((double) totalElements / size));
            response.put("currentPage", page);
            response.put("pageSize", size);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "유지보수 로그 조회 중 오류가 발생했습니다: " + e.getMessage());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 센서 재부팅 요청
     * POST /api/iot-manage/reboot-sensor/{householdId}
     */
    @PostMapping("/reboot-sensor/{householdId}")
    public ResponseEntity<Map<String, Object>> rebootSensor(@PathVariable Integer householdId) {
        try {
            boolean success = iotManageService.rebootSensor(householdId);

            if (!success) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "존재하지 않는 가구입니다.");

                return ResponseEntity.badRequest().body(errorResponse);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "센서 재부팅 명령이 전송되었습니다.");
            response.put("householdId", householdId);
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "센서 재부팅 요청 중 오류가 발생했습니다: " + e.getMessage());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 센서 상태 강제 새로고침
     * POST /api/iot-manage/refresh-sensors
     */
    @PostMapping("/refresh-sensors")
    public ResponseEntity<Map<String, Object>> refreshSensors() {
        try {
            boolean success = iotManageService.refreshAllSensors();

            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("message", success ?
                    "모든 센서 상태 새로고침이 요청되었습니다." :
                    "센서 새로고침 요청에 실패했습니다.");
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "센서 새로고침 요청 중 오류가 발생했습니다: " + e.getMessage());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}