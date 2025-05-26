package com.mcg.iotseniorsafe.dto;

public class SensorDataDto {
    private String sensorType; // LED, 재실감지, 소음감지
    private String location;   // 거실, 침실 등
    private Double value;      // 측정값
    private Double threshold;  // 임계값
    private String timestamp;   // 측정 시각

    public SensorDataDto() {}

    public String getSensorType() { return sensorType; }
    public void setSensorType(String sensorType) { this.sensorType = sensorType; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Double getValue() { return value; }
    public void setValue(Double value) { this.value = value; }

    public Double getThreshold() { return threshold; }
    public void setThreshold(Double threshold) { this.threshold = threshold; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
