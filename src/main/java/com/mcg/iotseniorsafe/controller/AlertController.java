package com.mcg.iotseniorsafe.controller;

import com.mcg.iotseniorsafe.dto.AlertRequest;
import com.mcg.iotseniorsafe.dto.AlertResponse;
import com.mcg.iotseniorsafe.service.BedrockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173") // Vite 개발 서버 포트
public class AlertController {
    private static final Logger logger = LoggerFactory.getLogger(AlertController.class);

    private final BedrockService bedrockService;

    public AlertController(BedrockService bedrockService) {
        this.bedrockService = bedrockService;
    }

    // 기존 레거시 엔드포인트 (하드코딩된 센서 데이터용)
    @PostMapping("/ai-report/analyze")
    public ResponseEntity<AlertResponse> analyzeAlert(@RequestBody AlertRequest request) {
        logger.info("레거시 AI 분석 요청 수신: {}개 센서", request.getSensorData().size());

        try {
            AlertResponse response = bedrockService.analyzeAndGenerateAlert(request.getSensorData());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("레거시 AI 분석 중 오류 발생", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}