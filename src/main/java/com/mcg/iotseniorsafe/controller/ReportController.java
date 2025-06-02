package com.mcg.iotseniorsafe.controller;

import com.mcg.iotseniorsafe.dto.ManualReportDto;
import com.mcg.iotseniorsafe.entity.Report;
import com.mcg.iotseniorsafe.repository.ReportRepository;
import com.mcg.iotseniorsafe.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService service;
    private final ReportRepository repo;

    @GetMapping
    public List<Report> list(){
        return repo.findAll();
    }

    @PostMapping
    public Report create(@RequestBody ManualReportDto dto){
        return service.createManualReport(dto);
    }
}

