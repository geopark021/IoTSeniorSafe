package com.mcg.iotseniorsafe.repository;

import com.mcg.iotseniorsafe.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    // 특정 가구의 오늘 날짜 report 조회
    @Query("SELECT r FROM Report r WHERE r.householdId = :householdId AND DATE(r.createdAt) = CURRENT_DATE")
    Optional<Report> findByHouseholdIdAndCreatedAtToday(@Param("householdId") Integer householdId);
}