package com.mcg.iotseniorsafe.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
// 신고 테이블
@Entity @Table(name = "report")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Report {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reportId;

    private Integer managerId;
    private Integer householdId;    // 가구번호
    private Byte statusCode;           // 0:접수, 1:진행, 2:완료 …
    private LocalDateTime createdAt;    // 생성 시각
    private LocalDateTime updatedAt;    // 수정 시각
    private String agencyName;  // 담당 기관
}
