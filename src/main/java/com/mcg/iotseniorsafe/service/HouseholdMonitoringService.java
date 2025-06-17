package com.mcg.iotseniorsafe.service;

import com.mcg.iotseniorsafe.dto.HouseholdMonitoringDto;
import com.mcg.iotseniorsafe.repository.HouseholdMonitoringRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class HouseholdMonitoringService {

    @Autowired
    private HouseholdMonitoringRepository householdMonitoringRepository;

    /**
     * 통합 센서 모니터링 데이터 조회
     */
    public List<HouseholdMonitoringDto> getAllHouseholdMonitoringData() {
        List<HouseholdMonitoringDto> monitoringData =
                householdMonitoringRepository.findAllHouseholdMonitoringData();

        // 데이터 후처리 - 상태 메시지 및 시간 포맷팅
        return monitoringData.stream()
                .map(this::processMonitoringData)
                .collect(Collectors.toList());
    }

    /**
     * 특정 가구의 상세 센서 데이터 조회
     */
    public List<HouseholdMonitoringDto> getHouseholdSensorDetails(Integer householdId) {
        return householdMonitoringRepository.findHouseholdSensorSummary(householdId);
    }

    /**
     * 모니터링 데이터 후처리
     */
    private HouseholdMonitoringDto processMonitoringData(HouseholdMonitoringDto dto) {
        // 마지막 활동 시간 기반 상태 판단
        String statusMessage = determineStatusMessage(dto);
        dto.setStatus(statusMessage);

        return dto;
    }

    /**
     * 센서 데이터를 기반으로 상태 메시지 결정
     */
    private String determineStatusMessage(HouseholdMonitoringDto dto) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastActivity = dto.getLastActivityTime();

        if (lastActivity == null) {
            return "데이터 없음";
        }

        long hoursFromLastActivity = java.time.Duration.between(lastActivity, now).toHours();

        // 센서 값들의 평균을 계산하여 활동 수준 판단
        double activityLevel = calculateActivityLevel(dto);

        if (hoursFromLastActivity > 12) {
            return "이상 없음"; // 12시간 이상 비활성
        } else if (hoursFromLastActivity > 6) {
            return "이상 없음"; // 6시간 이상 비활성
        } else if (activityLevel > 2.0) {
            return "정상 진행 감지"; // 높은 활동 수준
        } else if (activityLevel > 1.0) {
            return "정상 진행 감지"; // 보통 활동 수준
        } else {
            return "이상 없음"; // 낮은 활동 수준
        }
    }

    /**
     * 활동 수준 계산
     */
    private double calculateActivityLevel(HouseholdMonitoringDto dto) {
        double total = 0.0;
        int count = 0;

        if (dto.getLightLevel() != null && dto.getLightLevel() > 0) {
            total += dto.getLightLevel();
            count++;
        }
        if (dto.getOccupancyLevel() != null && dto.getOccupancyLevel() > 0) {
            total += dto.getOccupancyLevel();
            count++;
        }
        if (dto.getNoiseLevel() != null && dto.getNoiseLevel() > 0) {
            total += dto.getNoiseLevel();
            count++;
        }
        if (dto.getToiletLevel() != null && dto.getToiletLevel() > 0) {
            total += dto.getToiletLevel();
            count++;
        }

        return count > 0 ? total / count : 0.0;
    }

    /**
     * 시간 차이를 한국어 문자열로 변환
     */
    public String formatTimeDifference(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "데이터 없음";
        }

        LocalDateTime now = LocalDateTime.now();
        long minutes = java.time.Duration.between(dateTime, now).toMinutes();

        if (minutes < 60) {
            return minutes + "분 전";
        } else if (minutes < 1440) { // 24시간
            return (minutes / 60) + "시간 전";
        } else {
            return (minutes / 1440) + "일 전";
        }
    }
}