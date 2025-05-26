package com.mcg.iotseniorsafe.controller;

import com.mcg.iotseniorsafe.dto.AlertRequest;
import com.mcg.iotseniorsafe.dto.AlertResponse;
import com.mcg.iotseniorsafe.service.BedrockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alert")
public class AlertController {
    private final BedrockService bedrockService;

    public AlertController(BedrockService bedrockService) {
        this.bedrockService = bedrockService;
    }

    @PostMapping
    public ResponseEntity<AlertResponse> alert(@RequestBody AlertRequest req) {
        List<Double> svList = req.getSensorValues();
        List<Double> thList = req.getThresholds();

        double[] sensorValues = svList.stream().mapToDouble(Double::doubleValue).toArray();
        double[] thresholds   = thList.stream().mapToDouble(Double::doubleValue).toArray();

        List<String> alerts = bedrockService.generateAlerts(sensorValues, thresholds);

        if (alerts.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(new AlertResponse(alerts));
    }
}
