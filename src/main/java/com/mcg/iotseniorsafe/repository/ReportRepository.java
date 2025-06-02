package com.mcg.iotseniorsafe.repository;

import com.mcg.iotseniorsafe.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> { }
