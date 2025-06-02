package com.mcg.iotseniorsafe.dto;
// 사용자가 “신고하기” 버튼으로 전송할 JSON 매핑 DTO

public record ManualReportDto(
        Integer managerId,
        Integer householdId,
        Byte    riskLevel,   // 0~2
        String  description  // 신고 내용
) {}
