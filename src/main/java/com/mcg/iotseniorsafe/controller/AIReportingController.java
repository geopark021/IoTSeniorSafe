package com.mcg.iotseniorsafe.controller;

import com.mcg.iotseniorsafe.dto.AlertResponse;
import com.mcg.iotseniorsafe.dto.RiskEntryDto;
import com.mcg.iotseniorsafe.entity.Report;
import com.mcg.iotseniorsafe.service.BedrockService;
import com.mcg.iotseniorsafe.service.ReportService;
import com.mcg.iotseniorsafe.service.RiskAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai-reporting")
//@CrossOrigin(origins = "*")
public class AIReportingController {

    private static final Logger logger = LoggerFactory.getLogger(AIReportingController.class);

    private final BedrockService bedrockService;
    private final RiskAnalysisService riskAnalysisService;
    private final ReportService reportService;

    @Autowired
    public AIReportingController(BedrockService bedrockService, RiskAnalysisService riskAnalysisService, ReportService reportService) {
        this.bedrockService = bedrockService;
        this.riskAnalysisService = riskAnalysisService;
        this.reportService = reportService;
    }

    /**
     * 위험 의심 내역 목록 조회 (기존 report 테이블 기반)
     */
    @GetMapping("/risk-entries")
    public ResponseEntity<List<RiskEntryDto>> getRiskEntries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "latest") String sort) {

        try {
            List<RiskEntryDto> riskEntries = riskAnalysisService.getRiskEntries(page, size, search, sort);
            return ResponseEntity.ok(riskEntries);
        } catch (Exception e) {
            logger.error("위험 의심 내역 조회 실패", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 특정 가구의 위험도 분석 (위험 의심 내역 클릭 시)
     */
    @PostMapping("/analyze-household/{householdId}")
    public ResponseEntity<AlertResponse> analyzeHouseholdRisk(@PathVariable int householdId) {
        try {
            logger.info("가구 위험도 분석 요청: householdId={}", householdId);

            AlertResponse response = bedrockService.analyzeHouseholdRisk(householdId);

            if (response == null) {
                return ResponseEntity.internalServerError().build();
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("가구 위험도 분석 실패: householdId={}", householdId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 신고용 상세 보고서 생성
     */
    @PostMapping("/generate-report/{householdId}")
    public ResponseEntity<AlertResponse> generateReportingDocument(
            @PathVariable int householdId,
            @RequestBody AlertResponse initialAnalysis) {

        try {
            logger.info("신고 문서 생성 요청: householdId={}", householdId);

            AlertResponse reportingDocument = bedrockService.generateReportingDocument(householdId, initialAnalysis);

            if (reportingDocument == null) {
                return ResponseEntity.internalServerError().build();
            }

            return ResponseEntity.ok(reportingDocument);

        } catch (Exception e) {
            logger.error("신고 문서 생성 실패: householdId={}", householdId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 특정 가구의 센서 데이터 요약 조회 (디버깅/확인용)
     */
    @GetMapping("/household-data/{householdId}")
    public ResponseEntity<Map<String, Object>> getHouseholdData(@PathVariable int householdId) {
        try {
            Map<String, Object> householdData = riskAnalysisService.getHouseholdSensorSummary(householdId);
            return ResponseEntity.ok(householdData);
        } catch (Exception e) {
            logger.error("가구 데이터 조회 실패: householdId={}", householdId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 모든 가구의 위험도 일괄 평가 (배치 작업용)
     */
    @PostMapping("/evaluate-all-households")
    public ResponseEntity<Map<String, Object>> evaluateAllHouseholds() {
        try {
            logger.info("전체 가구 위험도 평가 시작");

            Map<String, Object> result = riskAnalysisService.evaluateAllHouseholds();

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("전체 가구 위험도 평가 실패", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 최종 신고 제출 (DB 저장)
     */
    @PostMapping("/submit-final-report")
    public ResponseEntity<Map<String, Object>> submitFinalReport(@RequestBody Map<String, Object> requestData) {
        try {
            int householdId = (Integer) requestData.get("householdId");
            int managerId = (Integer) requestData.get("managerId");
            String agencyName = (String) requestData.get("agencyName");
            String reportContent = (String) requestData.get("reportContent");

            logger.info("최종 신고 제출: householdId={}, managerId={}, agency={}", householdId, managerId, agencyName);

            // ReportService의 AI 리포팅용 메서드 호출 (아래에서 추가)
            Report savedReport = reportService.createAIReport(managerId, householdId, agencyName, reportContent);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "reportId", savedReport.getReportId(),
                    "message", "신고가 성공적으로 접수되었습니다."
            ));
        } catch (Exception e) {
            logger.error("최종 신고 저장 실패", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}