package com.mcg.iotseniorsafe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HouseholdMonitoringDto {
    private Integer householdId;
    private String name;                    // 이름
    private String contactNumber;           // 연락처
    private String address;                 // 주소
    private String managerName;             // 담당자
    private String managerContact;          // 담당자 연락처

    // 센서 데이터
    private Double lightLevel;              // 안방 (LED 센서 값)
    private Double occupancyLevel;          // 거실 (재실 감지)
    private Double noiseLevel;              // 주방 (소음)
    private Double toiletLevel;             // 현관 (화장실)

    private LocalDateTime lastActivityTime; // 마지막 활동시간
    private String status;                  // 상태/메시지

    // 정렬 기준을 위한 필드
    private LocalDateTime sortTime;         // 정렬용 시간
}