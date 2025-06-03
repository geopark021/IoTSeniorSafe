package com.mcg.iotseniorsafe.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "report_history")
public class ReportHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_history_id")
    private Integer reportHistoryId;

    @Column(name = "report_id", nullable = false)
    private Integer reportId;

    @Column(name = "process_status", nullable = false)
    private Integer processStatus;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    // 기본 생성자
    public ReportHistory() {}

    // 생성자
    public ReportHistory(Integer reportId, Integer processStatus, LocalDateTime recordedAt) {
        this.reportId = reportId;
        this.processStatus = processStatus;
        this.recordedAt = recordedAt;
    }

    // Getters and Setters
    public Integer getReportHistoryId() {
        return reportHistoryId;
    }

    public void setReportHistoryId(Integer reportHistoryId) {
        this.reportHistoryId = reportHistoryId;
    }

    public Integer getReportId() {
        return reportId;
    }

    public void setReportId(Integer reportId) {
        this.reportId = reportId;
    }

    public Integer getProcessStatus() {
        return processStatus;
    }

    public void setProcessStatus(Integer processStatus) {
        this.processStatus = processStatus;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }
}