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
public class MaintenanceLogDto {
    private Integer id;
    private Integer householdId;
    private String householdName;
    private String address;
    private String sensorType;
    private String errorMessage;
    private String status;
    private String timestamp;
    private LocalDateTime lastActivity;
}