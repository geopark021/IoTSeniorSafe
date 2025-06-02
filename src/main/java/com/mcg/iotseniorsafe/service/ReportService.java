package com.mcg.iotseniorsafe.service;

import com.mcg.iotseniorsafe.dto.ManualReportDto;
import com.mcg.iotseniorsafe.entity.Report;
import com.mcg.iotseniorsafe.repository.ReportDetailRepository;
import com.mcg.iotseniorsafe.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mcg.iotseniorsafe.entity.ReportDetail;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository repo;
    private final ReportDetailRepository detailRepo;

    @Transactional
    public Report createManualReport(ManualReportDto dto){
        Report rpt = repo.save(Report.builder()
                .managerId(dto.managerId())
                .householdId(dto.householdId())
                .statusCode(dto.riskLevel())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .agencyName("공동생활지원센터")
                .build());

        detailRepo.save(ReportDetail.builder()
                .reportId(rpt.getReportId())
                .description(dto.description())
                .build());

        return rpt;
    }

    /* 스케줄러에서 호출 */
    @Transactional
    public void createAutoReport(int hhId, byte level, String msg){
        createManualReport(new ManualReportDto(null, hhId, level, msg));
    }
}
