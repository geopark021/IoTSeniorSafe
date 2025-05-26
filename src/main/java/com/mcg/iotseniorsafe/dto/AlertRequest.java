package com.mcg.iotseniorsafe.dto;

import java.util.List;

public class AlertRequest {
    private List<SensorDataDto> sensorData;
    private String userId;
    private String location; // 방 위치

    public AlertRequest() {}

    public List<SensorDataDto> getSensorData() { return sensorData; }
    public void setSensorData(List<SensorDataDto> sensorData) { this.sensorData = sensorData; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}
