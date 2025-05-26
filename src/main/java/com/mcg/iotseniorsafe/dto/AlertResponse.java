package com.mcg.iotseniorsafe.dto;

import java.util.List;

public class AlertResponse {
    private List<String> alerts;

    public AlertResponse() {}

    public AlertResponse(List<String> alerts) {
        this.alerts = alerts;
    }

    public List<String> getAlerts() {
        return alerts;
    }
    public void setAlerts(List<String> alerts) {
        this.alerts = alerts;
    }
}
