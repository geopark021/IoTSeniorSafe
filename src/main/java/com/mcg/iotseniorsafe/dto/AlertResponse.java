package com.mcg.iotseniorsafe.dto;

public class AlertResponse {
    private String riskLevel;
    private String situation;
    private String recommendation;
    private String reportingAgency;
    private String contactNumber;
    private String urgencyLevel;

    // 새로 추가된 필드들
    private String location;
    private String comparisonDetails;
    private double commonDataRatio;
    private String householdId; // JSON 응답용

    // 신고용 보고서 필드들
    private String reportTitle;
    private String summary;
    private String detailedSituation;
    private String riskAssessment;
    private String immediateActions;
    private String followUpPlan;
    private String contactInfo;

    // 기본 생성자
    public AlertResponse() {}

    // Getters and Setters
    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getSituation() {
        return situation;
    }

    public void setSituation(String situation) {
        this.situation = situation;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public String getReportingAgency() {
        return reportingAgency;
    }

    public void setReportingAgency(String reportingAgency) {
        this.reportingAgency = reportingAgency;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public String getUrgencyLevel() {
        return urgencyLevel;
    }

    public void setUrgencyLevel(String urgencyLevel) {
        this.urgencyLevel = urgencyLevel;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getComparisonDetails() {
        return comparisonDetails;
    }

    public void setComparisonDetails(String comparisonDetails) {
        this.comparisonDetails = comparisonDetails;
    }

    public double getCommonDataRatio() {
        return commonDataRatio;
    }

    public void setCommonDataRatio(double commonDataRatio) {
        this.commonDataRatio = commonDataRatio;
    }

    public String getHouseholdId() {
        return householdId;
    }

    public void setHouseholdId(String householdId) {
        this.householdId = householdId;
    }

    public String getReportTitle() {
        return reportTitle;
    }

    public void setReportTitle(String reportTitle) {
        this.reportTitle = reportTitle;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDetailedSituation() {
        return detailedSituation;
    }

    public void setDetailedSituation(String detailedSituation) {
        this.detailedSituation = detailedSituation;
    }

    public String getRiskAssessment() {
        return riskAssessment;
    }

    public void setRiskAssessment(String riskAssessment) {
        this.riskAssessment = riskAssessment;
    }

    public String getImmediateActions() {
        return immediateActions;
    }

    public void setImmediateActions(String immediateActions) {
        this.immediateActions = immediateActions;
    }

    public String getFollowUpPlan() {
        return followUpPlan;
    }

    public void setFollowUpPlan(String followUpPlan) {
        this.followUpPlan = followUpPlan;
    }

    public String getContactInfo() {
        return contactInfo;
    }

    public void setContactInfo(String contactInfo) {
        this.contactInfo = contactInfo;
    }
}