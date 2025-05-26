package com.mcg.iotseniorsafe.dto;

public class AlertResponse {
    private String riskLevel;        // 위험도 (낮음/보통/높음/매우높음)
    private String situation;        // 상황 설명
    private String recommendation;   // 대응 지침
    private String reportingAgency;  // 신고 기관
    private String contactNumber;    // 연락처

    public AlertResponse() {}

    // Getters and Setters
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public String getSituation() { return situation; }
    public void setSituation(String situation) { this.situation = situation; }

    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }

    public String getReportingAgency() { return reportingAgency; }
    public void setReportingAgency(String reportingAgency) { this.reportingAgency = reportingAgency; }

    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }
}