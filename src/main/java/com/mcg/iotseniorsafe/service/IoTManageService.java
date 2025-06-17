// src/main/java/com/mcg/iotseniorsafe/service/IoTManageService.java
package com.mcg.iotseniorsafe.service;

import com.mcg.iotseniorsafe.dto.MaintenanceLogDto;
import com.mcg.iotseniorsafe.dto.SensorStatsDto;
import com.mcg.iotseniorsafe.repository.IoTManageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IoTManageService {

    @Autowired
    private IoTManageRepository iotManageRepository;

    /**
     * 센서 상태 통계 조회
     */
    public SensorStatsDto getSensorStats() {
        return iotManageRepository.getSensorStats();
    }

    /**
     * 유지보수 로그 조회
     */
    public List<MaintenanceLogDto> getMaintenanceLogs(int page, int size, String sortBy) {
        return iotManageRepository.getMaintenanceLogs(page, size, sortBy);
    }

    /**
     * 유지보수 로그 전체 개수 조회
     */
    public int getMaintenanceLogCount() {
        return iotManageRepository.getMaintenanceLogCount();
    }

    /**
     * 센서 재부팅 요청
     */
    public boolean rebootSensor(Integer householdId) {
        // 가구 존재 여부 확인
        if (!iotManageRepository.existsHousehold(householdId)) {
            return false;
        }

        // 실제로는 IoT 장치에 재부팅 명령 전송
        // 여기서는 시뮬레이션
        return true;
    }

    /**
     * 전체 센서 새로고침
     */
    public boolean refreshAllSensors() {
        // 실제로는 모든 센서에 상태 확인 명령 전송
        // 여기서는 시뮬레이션
        return true;
    }
}