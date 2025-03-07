package com.mcg.iotseniorsafe.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

// 모니터링 페이지 - IoT 장치 현황 컨포넌트
@Getter
@Setter
@AllArgsConstructor
public class DevicesStats {

    private long totalHouseholds; // 전체 가구
    private long iotEnabledHouseholds; // IoT 센서 부착 가구 수
    private long powerConnectedHouseholds; // 전력 데이터 연결된 가구 수
    private long anomaliesPerHousehold; // 가구별 이상 감지 수

}

