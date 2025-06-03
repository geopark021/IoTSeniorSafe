package com.mcg.iotseniorsafe.dto;

import java.util.List;
import java.util.Map;

public class HouseholdComparisonDto {
    private int householdId;
    private List<Map<String, Object>> yesterdayData;
    private List<Map<String, Object>> todayData;

    public HouseholdComparisonDto() {}

    public HouseholdComparisonDto(int householdId, List<Map<String, Object>> yesterdayData, List<Map<String, Object>> todayData) {
        this.householdId = householdId;
        this.yesterdayData = yesterdayData;
        this.todayData = todayData;
    }

    public boolean hasValidData() {
        return yesterdayData != null && todayData != null &&
                !yesterdayData.isEmpty() && !todayData.isEmpty();
    }

    // Getters and Setters
    public int getHouseholdId() {
        return householdId;
    }

    public void setHouseholdId(int householdId) {
        this.householdId = householdId;
    }

    public List<Map<String, Object>> getYesterdayData() {
        return yesterdayData;
    }

    public void setYesterdayData(List<Map<String, Object>> yesterdayData) {
        this.yesterdayData = yesterdayData;
    }

    public List<Map<String, Object>> getTodayData() {
        return todayData;
    }

    public void setTodayData(List<Map<String, Object>> todayData) {
        this.todayData = todayData;
    }
}