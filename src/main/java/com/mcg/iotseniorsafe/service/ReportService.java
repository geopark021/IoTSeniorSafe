package com.mcg.iotseniorsafe.service;

import com.mcg.iotseniorsafe.dto.ManualReportDto;
import com.mcg.iotseniorsafe.entity.Report;
import com.mcg.iotseniorsafe.repository.ReportDetailRepository;
import com.mcg.iotseniorsafe.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mcg.iotseniorsafe.entity.ReportDetail;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);

    private final ReportRepository repo;
    private final ReportDetailRepository detailRepo;

    // 한전 MCS 담당자가 직접 신고하는 기능 (기존 report 확인 후 detail 추가)
    @Transactional
    public Report createManualReport(ManualReportDto dto){
        logger.info("수동 신고 생성: managerId={}, householdId={}", dto.managerId(), dto.householdId());

        // 1. 해당 가구의 기존 report 확인 (오늘 날짜 기준)
        Optional<Report> existingReport = repo.findByHouseholdIdAndCreatedAtToday(dto.householdId());

        Report rpt;
        if (existingReport.isPresent()) {
            // 기존 report 업데이트
            rpt = existingReport.get();
            rpt.setUpdatedAt(LocalDateTime.now());
            rpt = repo.save(rpt);
            logger.info("기존 Report에 수동 신고 추가: reportId={}", rpt.getReportId());
        } else {
            // 새 report 생성
            rpt = repo.save(Report.builder()
                    .managerId(dto.managerId())
                    .householdId(dto.householdId())
                    .statusCode(dto.riskLevel())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .agencyName("공동생활지원센터")
                    .build());
            logger.info("새 Report 생성 (수동): reportId={}", rpt.getReportId());
        }

        // 2. ReportDetail 추가
        detailRepo.save(ReportDetail.builder()
                .reportId(rpt.getReportId())
                .description("[수동 신고] " + dto.description())  // 수동 신고임을 표시
                .build());

        logger.info("수동 신고 완료: reportId={}", rpt.getReportId());
        return rpt;
    }

    /* 스케줄러에서 호출 - 사용 안함 */
    @Transactional
    public void createAutoReport(int hhId, byte level, String msg){
        logger.info("자동 신고 생성 (deprecated): householdId={}, level={}", hhId, level);
        // 더 이상 자동 신고 생성하지 않음
        // createManualReport(new ManualReportDto(1, hhId, level, msg));
    }

    // AI 리포팅용 신고 생성 (기존 report 확인 후 detail 추가)
    @Transactional
    public Report createAIReport(int managerId, int householdId, String agencyName, String reportContent) {
        logger.info("AI 신고 생성 시작: managerId={}, householdId={}, agency={}",
                managerId, householdId, agencyName);

        try {
            // 1. 해당 가구의 기존 report 확인 (오늘 날짜 기준)
            Optional<Report> existingReport = repo.findByHouseholdIdAndCreatedAtToday(householdId);

            Report rpt;
            if (existingReport.isPresent()) {
                // 2-1. 기존 report가 있으면 업데이트
                rpt = existingReport.get();
                rpt.setUpdatedAt(LocalDateTime.now());
                rpt.setStatusCode((byte)1); // 처리 중으로 변경
                rpt.setAgencyName(agencyName); // AI가 제안한 기관으로 업데이트
                rpt = repo.save(rpt);

                logger.info("기존 Report 업데이트 (처리 중): reportId={}", rpt.getReportId());
            } else {
                // 2-2. 기존 report가 없으면 새로 생성
                rpt = repo.save(Report.builder()
                        .managerId(managerId)
                        .householdId(householdId)
                        .statusCode((byte)1)  // 처리 중 상태
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .agencyName(agencyName)  // AI가 제안한 기관
                        .build());

                logger.info("새 Report 생성 (처리 중): reportId={}", rpt.getReportId());
            }

            // 3. 새로운 ReportDetail 추가 (항상 추가)
            ReportDetail detail = detailRepo.save(ReportDetail.builder()
                    .reportId(rpt.getReportId())
                    .description("[AI 신고] " + reportContent)  // AI 신고임을 표시
                    .build());

            logger.info("AI 신고 완료: reportId={}, detailId={}", rpt.getReportId(), detail.getDetailId());
            return rpt;

        } catch (Exception e) {
            logger.error("AI 신고 생성 실패: managerId={}, householdId={}", managerId, householdId, e);
            throw e;
        }
    }
}