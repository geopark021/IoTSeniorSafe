package com.mcg.iotseniorsafe.dto;

import java.util.List;

public class AlertRequest {
    // 센서별 측정값 리스트
    private List<Double> sensorValues;
    // 센서별 임계치 리스트
    private List<Double> thresholds;

    // 기본 생성자 (Jackson용)
    public AlertRequest() {}

    // getter / setter
    public List<Double> getSensorValues() {
        return sensorValues;
    }
    public void setSensorValues(List<Double> sensorValues) {
        this.sensorValues = sensorValues;
    }

    public List<Double> getThresholds() {
        return thresholds;
    }
    public void setThresholds(List<Double> thresholds) {
        this.thresholds = thresholds;
    }
}
