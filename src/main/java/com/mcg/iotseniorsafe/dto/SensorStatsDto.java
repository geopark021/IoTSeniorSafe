package com.mcg.iotseniorsafe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorStatsDto { // IoT 기기 오류 , 유지보수 로그용
    private Integer totalHouseholds;
    private Integer ledSensorCount;
    private Integer occupancySensorCount;
    private Integer noiseSensorCount;
    private Integer errorCount;
}