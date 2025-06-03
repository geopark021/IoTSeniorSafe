package com.mcg.iotseniorsafe.dto;

import java.time.LocalDateTime;

public class RiskEntryDto {
    private int reportId;
    private int householdId;
    private int managerId;
    private String managerName;
    private String householdName;
    private String address;
    private String contactNumber;
    private int statusCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String agencyName;
    private String description; // report_detail에서
    private double commonDataRatio; // 계산된 값
    private String riskLevel; // 계산된 위험도

    // 생성자
    public RiskEntryDto() {}

    public RiskEntryDto(int reportId, int householdId, int managerId, String managerName,
                        String householdName, String address, String contactNumber,
                        int statusCode, LocalDateTime createdAt, LocalDateTime updatedAt,
                        String agencyName, String description, double commonDataRatio, String riskLevel) {
        this.reportId = reportId;
        this.householdId = householdId;
        this.managerId = managerId;
        this.managerName = managerName;
        this.householdName = householdName;
        this.address = address;
        this.contactNumber = contactNumber;
        this.statusCode = statusCode;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.agencyName = agencyName;
        this.description = description;
        this.commonDataRatio = commonDataRatio;
        this.riskLevel = riskLevel;
    }

    // Getters and Setters
    public int getReportId() { return reportId; }
    public void setReportId(int reportId) { this.reportId = reportId; }

    public int getHouseholdId() { return householdId; }
    public void setHouseholdId(int householdId) { this.householdId = householdId; }

    public int getManagerId() { return managerId; }
    public void setManagerId(int managerId) { this.managerId = managerId; }

    public String getManagerName() { return managerName; }
    public void setManagerName(String managerName) { this.managerName = managerName; }

    public String getHouseholdName() { return householdName; }
    public void setHouseholdName(String householdName) { this.householdName = householdName; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }

    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getAgencyName() { return agencyName; }
    public void setAgencyName(String agencyName) { this.agencyName = agencyName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getCommonDataRatio() { return commonDataRatio; }
    public void setCommonDataRatio(double commonDataRatio) { this.commonDataRatio = commonDataRatio; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
}